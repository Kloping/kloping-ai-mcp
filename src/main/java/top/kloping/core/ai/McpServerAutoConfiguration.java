package top.kloping.core.ai;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.kloping.core.ai.dto.ToolCallRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 *
 * create on 15:50
 *
 * @author github kloping
 * @since 2025/10/12
 */
@Configuration
@Slf4j
@EnableConfigurationProperties(McpServersConfig.class)
public class McpServerAutoConfiguration {

    public static final Executor EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    @Bean
    public McpBean mcpToolCallbacks(McpServersConfig config) {
        McpBean mcpBean = new McpBean();
        CountDownLatch cdl = new CountDownLatch(config.getServers().size());
        EXECUTOR_SERVICE.execute(() -> {
            config.getServers().forEach((k, v) -> {
                McpBean.McpOne mcpOne = new McpBean.McpOne();
                mcpOne.setName(k);
                Map<String, ToolCallback> toolCallbacks = new HashMap<>();
                McpClient client = new McpClient(v);
                client.setReconnectType(v.getOnline() ? McpClient.ReconnectType.RECONNECT_NOW : McpClient.ReconnectType.RECONNECT_USE);
                EXECUTOR_SERVICE.execute(() -> {
                    try {
                        log.info("McpClient '{}' Initialization begins.", k);
                        client.initialize();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
                try {
                    client.getCdl().await();
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
                log.info("McpClient '{}' Initialization successful.", k);
                client.getTool().forEach((name, tool) -> {
                    toolCallbacks.put(name, new ToolCallback() {
                        @NotNull
                        @Override
                        public ToolDefinition getToolDefinition() {
                            return ToolDefinition.builder()
                                    .description(tool.getDescription()).name(name)
                                    .inputSchema(JSON.toJSONString(tool.getInputSchema()))
                                    .build();
                        }

                        @NotNull
                        @Override
                        public String call(@NotNull String toolInput) {
                            ToolCallRequest.Params toolCall = new ToolCallRequest.Params();
                            toolCall.setName(name).setArguments(JSON.parseObject(toolInput));
                            return client.toolCall(toolCall);
                        }
                    });
                });
                mcpOne.setToolCallbacks(toolCallbacks);
                mcpBean.getMcpOnes().put(k, mcpOne);
                cdl.countDown();
            });
        });
        try {
            cdl.await();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return mcpBean;
    }
}
