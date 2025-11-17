package top.kloping.core.ai;

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

    @Data
    public static class McpConfig {
        private String type = "sse";

        private String server;
        private String endpoint;
        private String token;

        private Boolean online = false;
        private Integer heartbeat = 5;
        private String clientName = "mcp-client";
        private String clientVersion = "0.1.0";
        private String protocolVersion = LATEST_PROTOCOL_VERSION;
        // 该工具调用的 时间
        private Integer readTimeout = 8; //表示无限制
        private Integer connectTimeout = 20;
        private Integer writeTimeout = 15;
        private Integer callTimeout = 7; //表示无限制
    }


    public static final String LATEST_PROTOCOL_VERSION = "2024-11-05";

    public static final String JSONRPC_VERSION = "2.0";
}