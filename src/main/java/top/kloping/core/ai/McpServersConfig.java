package top.kloping.core.ai;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ConfigurationProperties(prefix = "top.kloping.ai.mcp")
public class McpServersConfig {

    @Getter
    private Map<String, McpConfig> servers = new HashMap<>();

    public McpServersConfig setServers(Map<String, McpConfig> servers) {
        log.info("McpServersConfig set servers!");
        servers.forEach((key, value) -> {
            value.setClientName(key);
        });
        this.servers = servers;
        return this;
    }

    @PostConstruct
    public void validate() {
        for (Map.Entry<String, McpConfig> entry : servers.entrySet()) {
            McpConfig config = entry.getValue();
            if (config.getServer() == null || config.getServer().trim().isEmpty()) {
                throw new IllegalArgumentException("Server URL cannot be empty for " + entry.getKey());
            }
        }
    }

    @Data
    public static class McpConfig {
        private String type = "sse";

        // 服务地址
        private String server;
        // 接入点
        private String endpoint = "/sse";
        private String token;

        /**
         * 保持链接 如果掉线是否立刻重连
         */
        private Boolean online = false;
        private Integer heartbeat = 26;
        private String clientName = "mcp-client";
        private String clientVersion = "0.1.0";
        private String protocolVersion = LATEST_PROTOCOL_VERSION;
        // 该工具调用的 时间
        private Integer readTimeout = 8;
        private Integer connectTimeout = 20;
        private Integer writeTimeout = 15;
        private Integer callTimeout = 7;
    }


    public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

    public static final String JSONRPC_VERSION = "2.0";
}