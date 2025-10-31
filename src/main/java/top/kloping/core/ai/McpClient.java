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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class McpClient {
    public enum ReconnectType {
        RECONNECT_USE,
        RECONNECT_NOW
    }

    public McpClient(McpServersConfig.McpConfig properties) {
        this.server = properties.getServer();
        this.endpoint = properties.getEndpoint();
        this.token = properties.getToken();
        this.clientName = properties.getClientName() != null ? properties.getClientName() : this.clientName;
        this.clientVersion = properties.getClientVersion() != null ? properties.getClientVersion() : this.clientVersion;
        this.protocolVersion = properties.getProtocolVersion() != null ? properties.getProtocolVersion() : this.protocolVersion;
        this.heartbeat = properties.getHeartbeat();
    }

    private ReconnectType reconnectType = ReconnectType.RECONNECT_USE;

    private String server;
    private String endpoint;
    private String token;

    private String clientName = "mcp-client";
    private String clientVersion = "0.1.0";
    private String protocolVersion = "2025-05-05";
    private int heartbeat;

    final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build();

    private CountDownLatch cdl = new CountDownLatch(1);

    public void initialize() throws IOException, InterruptedException {
        _id.set(0);
        Request request = new Request.Builder().url(server + endpoint)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .addHeader("Connection", "keep-alive")
                .get().build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            try {
                log.error(response.body() != null ? response.body().string() : "response body is null");
            } finally {
                response.close();
            }
            return;
        }
        BufferedReader bufferedReader = null;
        if (response.body() != null) {
            bufferedReader = new BufferedReader(response.body().charStream());
        }
        String event = null;
        String data = null;
        while (bufferedReader != null) {
            String[] kv;
            try {
                String line = bufferedReader.readLine();
                if (line == null) {
                    log.warn("mcp readline is null break!");
                    break;
                }
                if (line.isEmpty()) continue;
                log.debug("mcp client {} recv: {}", clientName, line);
                kv = line.split(":", 2);
            } catch (SocketTimeoutException e) {
                break;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                break;
            }
            if (kv.length < 2) {
                log.debug("skip malformed sse line: {}", (Object) kv);
                continue;
            }
            String type = kv[0];
            String content = kv[1];
            if (type.equals("event")) event = content;
            else if (type.equals("data")) data = content;
            try {
                if (event != null && data != null) {
                    doEvent(event, data);
                    event = null;
                    data = null;
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        if (bufferedReader != null) bufferedReader.close();
        response.close();
        _over = true;
        cdl = new CountDownLatch(1);
        if (reconnectType == ReconnectType.RECONNECT_USE) {
            log.warn("mcp client {} over,when call before reconnect.", clientName);
        } else if (reconnectType == ReconnectType.RECONNECT_NOW) {
            log.warn("mcp client {} over,delay 5s reconnect.", clientName);
            Thread.sleep(5000);
            initialize();
        }
    }

    private volatile boolean _over = false;
    private final AtomicInteger _id = new AtomicInteger(0);
    private volatile String _endpoint;
    private volatile String _protocol_version;

    private void doEvent(String event, String data) throws IOException, InterruptedException {
        if (event.equals("endpoint")) {
            doEndpoint(data);
        } else if (event.equals("message")) {
            doMessage(data);
        }
    }

    private InitializeResponse initializeResponse;

    private int _tool_list_id;

    private void doMessage(String data) throws IOException {
        JSONObject jsonObject = JSONObject.parseObject(data);
        Integer id = jsonObject.getInteger("id");
        if (id == 0) {
            initializeResponse = JSON.parseObject(data, InitializeResponse.class);
            _protocol_version = initializeResponse.getResult().getProtocolVersion();
            protocolVersion = _protocol_version;
            McpReqPack.Params params = new McpReqPack.Params();
            params.setProtocolVersion(_protocol_version);
            doReqBody(JSON.toJSONString(new InitializedRequest(null, params)));
            doReqBody(JSON.toJSONString(new ToolListRequest((_tool_list_id = _id.getAndIncrement()), null)));
        } else if (id == _tool_list_id) {
            ToolListResponse toolListResponse = JSON.parseObject(data, ToolListResponse.class);
            ToolListResponse.Tool[] tools = toolListResponse.getResult().getTools();
            for (ToolListResponse.Tool tool : tools) {
                analyseMcpServerTools(tool);
            }
            _over = false;
            cdl.countDown();
            if (reconnectType == ReconnectType.RECONNECT_NOW || heartbeat > 0) {
                new Thread(() -> {
                    while (!_over) {
                        try {
                            McpReqPack<Object> reqPack = new McpReqPack<>(_id.getAndIncrement(), "ping", new Object());
                            doReqBody(JSON.toJSONString(reqPack));
                            Thread.sleep(heartbeat * 1000L);
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }).start();
            }
        } else {
            if (id2runnable.containsKey(id)) {
                id2runnable.get(id).onResponse(data);
                id2runnable.remove(id);
            }
        }
    }

    private final Map<Integer, ToolCallResponse> id2runnable = new ConcurrentHashMap<>();
    private final Map<String, ToolListResponse.Tool> tool = new ConcurrentHashMap<>();

    private void analyseMcpServerTools(ToolListResponse.Tool tool) {
        this.tool.put(tool.getName(), tool);
    }

    public String toolCall(ToolCallRequest.Params params) {
        if (_over) {
            executor.execute(() -> {
                try {
                    initialize();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        int id = _id.getAndIncrement();
        ToolCallRequest request = new ToolCallRequest(id, params);
        AtomicReference<String> toolMessage = new AtomicReference<>();
        try {
            CountDownLatch cdl = new CountDownLatch(1);
            id2runnable.put(id, (d) -> {
                JSONObject jsonObject = JSONObject.parseObject(d);
                jsonObject = jsonObject.getJSONObject("result");
                JSONArray content = jsonObject.getJSONArray("content");
                toolMessage.set(content.toString());
                cdl.countDown();
            });
            doReqBody(JSON.toJSONString(request));
            long waitSeconds = heartbeat > 0 ? heartbeat : 30L;
            cdl.await(waitSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return toolMessage.get();
    }

    public List<RequestTool> getRequestTools() {
        List<RequestTool> requestTools = new ArrayList<>();
        tool.forEach((k, v) -> {
            RequestTool requestTool = new RequestTool();
            RequestTool.Function function = new RequestTool.Function();
            function.setName(v.getName());
            function.setDescription(v.getDescription());
            RequestTool.Parameter toolParameter = new RequestTool.Parameter();
            toolParameter.setType("object");
            toolParameter.setRequired(v.getInputSchema().getRequired());
            toolParameter.setProperties(v.getInputSchema().getProperties());
            function.setParameters(toolParameter);
            requestTool.setFunction(function);
            requestTools.add(requestTool);
        });
        return requestTools;
    }

    private void doEndpoint(String data) throws IOException {
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

    public static final Executor executor = Executors.newSingleThreadExecutor();

    private void doReqBody(String reqBody) throws IOException {
        log.debug("mcp client {} send: {}", clientName, reqBody);
        Request request = new Request.Builder().url(server + _endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(reqBody, MediaType.get("application/json"))).build();
        try (Response response = client.newCall(request).execute()) {
            // ensure the response is closed to avoid leaks
        }
    }
}
