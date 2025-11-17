package top.kloping.core.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import top.kloping.core.ai.dto.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP (Model Context Protocol) 客户端
 * 提供与MCP服务器的连接和工具调用功能
 * 线程安全的实现
 *
 * @author github kloping
 * @since 2025/9/20-10:44
 */
@Slf4j
@Data
@Accessors(chain = true)
public class McpClient{
    private static final String TYPE_EVENT = "event";
    private static final String TYPE_DATA = "data";
    private static final String EVENT_ENDPOINT = "endpoint";
    private static final String EVENT_MESSAGE = "message";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONNECTION = "Connection";
    private static final String AUTH_BEARER = "Bearer ";
    private static final String ACCEPT_SSE = "text/event-stream";
    private static final String CONNECTION_KEEP_ALIVE = "keep-alive";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_PING = "ping";
    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_INITIALIZED = "notifications/initialized";
    private static final long RECONNECT_DELAY_MS = 5000L;

    public enum ReconnectType {
        RECONNECT_USE,
        RECONNECT_NOW
    }

    public static final OkHttpClient EVENT_STREAM_HTTP_CLIENT = new OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS).build();

    public McpClient(McpServersConfig.McpConfig properties) {
        this.server = properties.getServer();
        this.endpoint = properties.getEndpoint();
        this.token = properties.getToken();

        this.clientName = properties.getClientName();
        this.clientVersion = properties.getClientVersion();
        this.protocolVersion = properties.getProtocolVersion();
        this.heartbeat = properties.getHeartbeat();
        // timeout set
        this.connectTimeout = properties.getConnectTimeout();
        this.readTimeout = properties.getReadTimeout();
        this.writeTimeout = properties.getWriteTimeout();
        this.callTimeout = properties.getCallTimeout();

        // 配置OkHttpClient，当readTimeout为0时设置为无限等待
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .callTimeout(callTimeout, TimeUnit.SECONDS);

        OK_HTTP_CLIENT = clientBuilder.build();

        // 初始化心跳调度器
        if (heartbeat > 0) {
            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, clientName + "-heartbeat");
                t.setDaemon(true);
                return t;
            });
        } else {
            heartbeatScheduler = null;
        }
    }

    private ReconnectType reconnectType = ReconnectType.RECONNECT_USE;

    private String server;
    private String endpoint;
    private String token;

    private String clientName;
    private String clientVersion;
    private String protocolVersion;

    private int heartbeat;

    private final Integer readTimeout;
    private final Integer connectTimeout;
    private final Integer writeTimeout;
    private final Integer callTimeout;

    final OkHttpClient OK_HTTP_CLIENT;

    // 心跳调度器
    private final ScheduledExecutorService heartbeatScheduler;

    // 使用 volatile 和双重检查锁定确保线程安全
    private volatile CountDownLatch cdl = new CountDownLatch(1);
    private final Object cdlLock = new Object();

    public void initialize() throws IOException, InterruptedException {
        _id.set(0);
        Request request = new Request.Builder().url(server + endpoint)
                .header(HEADER_AUTHORIZATION, AUTH_BEARER + token)
                .header(HEADER_ACCEPT, ACCEPT_SSE)
                .addHeader(HEADER_CONNECTION, CONNECTION_KEEP_ALIVE)
                .get().build();

        try (Response response = EVENT_STREAM_HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = response.body() != null ? response.body().string() : "response body is null";
                log.error("McpClient[{}] initialization failed: {}", clientName, errorMsg);
                return;
            }

            // 使用 try-with-resources 确保 BufferedReader 正确关闭
            try (BufferedReader bufferedReader = response.body() != null ?
                    new BufferedReader(response.body().charStream()) : null) {

                if (bufferedReader == null) {
                    log.warn("McpClient[{}] response body is null", clientName);
                    return;
                }

                String event = null;
                String data = null;

                while (true) {
                    String[] kv;
                    try {
                        String line = bufferedReader.readLine();
                        if (line == null) {
                            log.warn("McpClient[{}] readline is null, connection may be closed", clientName);
                            break;
                        }
                        if (line.trim().isEmpty()) continue;
                        log.debug("McpClient[{}] recv: {}", clientName, line);
                        kv = line.split(":", 2);
                    } catch (SocketTimeoutException e) {
                        log.warn("McpClient[{}] socket timeout", clientName, e);
                        break;
                    } catch (java.io.InterruptedIOException e) {
                        // 处理OkHttp的超时异常
                        log.warn("McpClient[{}] connection timeout or interrupted", clientName, e);
                        break;
                    } catch (IOException e) {
                        log.error("McpClient[{}] IO error while reading", clientName, e);
                        break;
                    }

                    if (kv.length < 2) {
                        log.debug("McpClient[{}] skip malformed SSE line: {}", clientName, (Object) kv);
                        continue;
                    }

                    String type = kv[0].trim();
                    String content = kv[1].trim();

                    if (type.equals(TYPE_EVENT)) {
                        event = content;
                    } else if (type.equals(TYPE_DATA)) {
                        data = content;
                    }

                    try {
                        if (event != null && data != null) {
                            doEvent(event, data);
                            event = null;
                            data = null;
                        }
                    } catch (Exception e) {
                        log.error("McpClient[{}] error processing event", clientName, e);
                    }
                }
            }
        } catch (java.io.InterruptedIOException e) {
            // 处理OkHttp的超时异常
            log.warn("McpClient[{}] connection timeout during initialization", clientName, e);
        } catch (IOException e) {
            log.error("McpClient[{}] IO error during initialization", clientName, e);
        }

        // 标记连接已关闭
        _over = true;

        // 重新初始化 CountDownLatch，使用双重检查锁定确保线程安全
        synchronized (cdlLock) {
            if (cdl.getCount() > 0) {
                // 如果还有等待的线程，先释放它们
                while (cdl.getCount() > 0) {
                    cdl.countDown();
                }
            }
            cdl = new CountDownLatch(1);
        }
        // 处理重连逻辑
        handleReconnect();
    }

    private void handleReconnect() throws InterruptedException, IOException {
        if (reconnectType == ReconnectType.RECONNECT_USE) {
            log.warn("McpClient[{}] connection closed, will reconnect on next call", clientName);
        } else if (reconnectType == ReconnectType.RECONNECT_NOW) {
            log.warn("McpClient[{}] connection closed, reconnecting in {}ms", clientName, RECONNECT_DELAY_MS);
            Thread.sleep(RECONNECT_DELAY_MS);
            initialize();
        }
    }

    private volatile boolean _over = false;
    private final AtomicInteger _id = new AtomicInteger(0);
    private volatile String _endpoint;
    private volatile String _protocol_version;

    private void doEvent(String event, String data) throws IOException, InterruptedException {
        if (event.equals(EVENT_ENDPOINT)) {
            doEndpoint(data);
        } else if (event.equals(EVENT_MESSAGE)) {
            doMessage(data);
        }
    }

    private InitializeResponse initializeResponse;

    private int _tool_list_id;

    private void doMessage(String data) throws IOException {
        JSONObject jsonObject = JSONObject.parseObject(data);
        Integer id = jsonObject.getInteger("id");

        if (id == null) {
            log.warn("McpClient[{}] received message without ID", clientName);
            return;
        }

        if (id == 0) {
            // 初始化响应
            initializeResponse = JSON.parseObject(data, InitializeResponse.class);
            if (initializeResponse == null || initializeResponse.getResult() == null) {
                log.error("McpClient[{}] invalid initialize response", clientName);
                return;
            }

            _protocol_version = initializeResponse.getResult().getProtocolVersion();
            protocolVersion = _protocol_version;

            // 发送 initialized 通知
            McpReqPack.Params params = new McpReqPack.Params();
            params.setProtocolVersion(_protocol_version);
            doReqBody(JSON.toJSONString(new InitializedRequest(null, params)));

            // 请求工具列表
            _tool_list_id = _id.getAndIncrement();
            doReqBody(JSON.toJSONString(new ToolListRequest(_tool_list_id, null)));
        } else if (id == _tool_list_id) {
            // 工具列表响应
            ToolListResponse toolListResponse = JSON.parseObject(data, ToolListResponse.class);
            if (toolListResponse == null || toolListResponse.getResult() == null) {
                log.error("McpClient[{}] invalid tool list response", clientName);
                return;
            }

            ToolListResponse.Tool[] tools = toolListResponse.getResult().getTools();
            if (tools != null) {
                for (ToolListResponse.Tool tool : tools) {
                    if (tool != null) {
                        analyseMcpServerTools(tool);
                    }
                }
            }

            // 标记初始化完成
            _over = false;

            // 释放等待的线程
            synchronized (cdlLock) {
                cdl.countDown();
            }

            // 启动心跳
            startHeartbeat();
        } else {
            // 工具调用响应
            ToolCallResponse callback = id2runnable.get(id);
            if (callback != null) {
                callback.onResponse(data);
                id2runnable.remove(id);
            } else {
                if (heartbeatIds.contains(id)) {
                    heartbeatIds.remove(id);
                    log.debug("McpClient[{}] heartbeat removed for id={}", clientName, id);
                } else log.warn("McpClient[{}] received response for unknown request ID: {}", clientName, id);
            }
        }
    }

    private Queue<Integer> heartbeatIds = new ArrayDeque<>(5);

    private void startHeartbeat() {
        if ((heartbeat > 0) && heartbeatScheduler != null) {
            heartbeatScheduler.scheduleAtFixedRate(() -> {
                if (!_over) {
                    try {
                        int id0 = -1;
                        McpReqPack<Object> reqPack = new McpReqPack<>((id0 = _id.getAndIncrement()), METHOD_PING, new Object());
                        heartbeatIds.offer(id0);
                        doReqBody(JSON.toJSONString(reqPack));
                    } catch (Exception e) {
                        log.error("McpClient[{}] error sending heartbeat", clientName, e);
                    }
                }
            }, heartbeat, heartbeat, TimeUnit.SECONDS);
        }
    }

    private final Map<Integer, ToolCallResponse> id2runnable = new ConcurrentHashMap<>();
    private final Map<String, ToolListResponse.Tool> tool = new ConcurrentHashMap<>();

    private void analyseMcpServerTools(ToolListResponse.Tool tool) {
        if (tool != null && tool.getName() != null) {
            this.tool.put(tool.getName(), tool);
        }
    }

    public String toolCall(ToolCallRequest.Params params) {
        // 检查连接状态，如果已断开则尝试重新连接
        if (_over) {
            log.info("McpClient[{}] connection is closed, attempting to reconnect", clientName);
            EXECUTOR_SERVICE.execute(() -> {
                try {
                    initialize();
                } catch (Exception e) {
                    log.error("McpClient[{}] error reconnecting", clientName, e);
                }
            });

            try {
                // 等待重新连接完成
                synchronized (cdlLock) {
                    cdl.await();
                }
            } catch (InterruptedException e) {
                log.error("McpClient[{}] interrupted while waiting for reconnect", clientName, e);
                Thread.currentThread().interrupt(); // 恢复中断状态
                return null;
            }
        }

        int id = _id.getAndIncrement();
        ToolCallRequest request = new ToolCallRequest(id, params);
        AtomicReference<String> toolMessage = new AtomicReference<>();

        try {
            log.info("McpClient[{}] start tool/call: {}", clientName, request);
            CountDownLatch responseCdl = new CountDownLatch(1);
            id2runnable.put(id, (d) -> {
                try {
                    log.info("McpClient[{}] finish id({}) tool/call: {}", clientName, id, d);
                    JSONObject jsonObject = JSONObject.parseObject(d);
                    if (jsonObject != null) {
                        jsonObject = jsonObject.getJSONObject("result");
                        if (jsonObject != null) {
                            JSONArray content = jsonObject.getJSONArray("content");
                            toolMessage.set(content != null ? content.toString() : null);
                        }
                    }
                } catch (Exception e) {
                    log.error("McpClient[{}] error processing tool response", clientName, e);
                } finally {
                    responseCdl.countDown();
                }
            });
            doReqBody(JSON.toJSONString(request));
            long waitSeconds = (heartbeat > 0 && heartbeat < 30) ? heartbeat : 30L;
            if (!responseCdl.await(waitSeconds, TimeUnit.SECONDS)) {
                log.warn("McpClient[{}] tool call timeout after {} seconds", clientName, waitSeconds);
                id2runnable.remove(id);
                return null;
            }
        } catch (InterruptedException e) {
            log.error("McpClient[{}] tool call interrupted", clientName, e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            id2runnable.remove(id);
            return null;
        } catch (Exception e) {
            log.error("McpClient[{}] error during tool call", clientName, e);
            id2runnable.remove(id);
            return null;
        }

        return toolMessage.get();
    }

    public List<RequestTool> getRequestTools() {
        List<RequestTool> requestTools = new ArrayList<>();
        tool.forEach((k, v) -> {
            if (v != null) {
                RequestTool requestTool = new RequestTool();
                RequestTool.Function function = new RequestTool.Function();
                function.setName(v.getName());
                function.setDescription(v.getDescription());

                RequestTool.Parameter toolParameter = new RequestTool.Parameter();
                toolParameter.setType("object");

                if (v.getInputSchema() != null) {
                    toolParameter.setRequired(v.getInputSchema().getRequired());
                    toolParameter.setProperties(v.getInputSchema().getProperties());
                }

                function.setParameters(toolParameter);
                requestTool.setFunction(function);
                requestTools.add(requestTool);
            }
        });
        return requestTools;
    }

    private void doEndpoint(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            log.warn("McpClient[{}] received empty endpoint data", clientName);
            return;
        }

        _endpoint = data;

        InitializeRequest.ClientInfo clientInfo = new InitializeRequest.ClientInfo();
        clientInfo.setName(clientName);
        clientInfo.setVersion(clientVersion);

        InitializeRequest.Params params = new InitializeRequest.Params();
        params.setClientInfo(clientInfo);
        params.setCapabilities(Map.of());
        params.setProtocolVersion(protocolVersion);

        InitializeRequest initializeRequest = new InitializeRequest(_id.getAndIncrement(), params);
        String reqBody = JSON.toJSONString(initializeRequest);
        doReqBody(reqBody);
    }

    public static final Executor EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mcp-client-initializer");
        t.setDaemon(true);
        return t;
    });

    private Response doReqBody(String reqBody) throws IOException {
        if (_endpoint == null) {
            throw new IOException("McpClientendpoint not initialized");
        }

        log.debug("McpClient[{}] send: {}", clientName, reqBody);
        Request request = new Request.Builder().url(server + _endpoint)
                .addHeader(HEADER_AUTHORIZATION, AUTH_BEARER + token)
                .post(RequestBody.create(reqBody, MediaType.get(CONTENT_TYPE_JSON))).build();

        try (Response response = OK_HTTP_CLIENT.newCall(request).execute()) {
            return response;
        } catch (java.io.InterruptedIOException e) {
            // 处理OkHttp的超时异常
            log.warn("McpClient[{}] request timeout or interrupted", clientName, e);
            throw e;
        } catch (IOException e) {
            log.error("McpClient[{}] IO error during request", clientName, e);
            throw e;
        }
    }


    private void onRecvId(Integer id, String data) {
    }

    /**
     * 关闭客户端，释放资源
     */
    public void close() {
        _over = true;

        // 关闭心跳调度器
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 释放所有等待的线程
        synchronized (cdlLock) {
            while (cdl.getCount() > 0) {
                cdl.countDown();
            }
        }

        // 清理回调
        id2runnable.clear();
        tool.clear();

        log.info("McpClient[{}] closed", clientName);
    }
}
