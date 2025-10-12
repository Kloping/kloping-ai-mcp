package top.kloping.core.ai.dto;

import lombok.Data;

/**
 * @author github kloping
 * @since 2025/9/20-11:17
 */
public class InitializeResponse extends McpResPack<InitializeResponse.Result> {
    public InitializeResponse(String jsonrpc, Integer id, Result result) {
        super(jsonrpc, id, result);
    }

    @Data
    public static class Result {
        private String protocolVersion;
        private Capabilities capabilities;
        private ServerInfo serverInfo;
    }

    @Data
    public static class ServerInfo {
        private String name;
        private String version;
    }

    @Data
    public static class Capabilities {
        private Experimental experimental;
        private Prompts prompts;
        private Resources resources;
        private Tools tools;
    }

    @Data
    public static class Experimental {
    }

    @Data
    public static class Prompts {
        private Boolean listChanged;
    }

    @Data
    public static class Resources {
        private Boolean subscribe;
        private Boolean listChanged;
    }

    @Data
    public static class Tools {
        private Boolean listChanged;
    }
}
