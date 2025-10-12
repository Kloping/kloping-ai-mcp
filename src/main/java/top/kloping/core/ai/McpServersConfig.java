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
        private Integer heartbeat = 30;
        private String clientName = "mcp-client";
        private String clientVersion = "0.1.0";
        private String protocolVersion = "2025-05-05";
    }
}