package top.kloping.core.ai.dto;

import lombok.Data;
import top.kloping.core.ai.RequestTool;

import java.util.List;
import java.util.Map;

/**
 *
 *
 * @author github kloping
 * @since 2025/9/20-11:44
 */
public class ToolListResponse extends McpResPack<ToolListResponse.Result> {
    public ToolListResponse(String jsonrpc, Integer id, Result result) {
        super(jsonrpc, id, result);
    }

    @Data
    public static class Result {
        private Tool[] tools;
    }

    @Data
    public static class Tool {
        private String name;
        private String description;
        private InputSchema inputSchema;
    }

    @Data
    public static class InputSchema {
        private Map<String, RequestTool.ParameterDesc> properties;
        private List<String> required;
        private String type = "object";
        private String title;
    }

}
