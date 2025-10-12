package top.kloping.core.ai.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 *
 * @author github kloping
 * @since 2025/9/20-11:53
 */
public class ToolCallRequest extends McpReqPack<ToolCallRequest.Params> {
    public ToolCallRequest(Integer id, Params params) {
        super(id, "tools/call", params);
    }

    @Data
    @Accessors(chain = true)
    public static class Params {
        private String name;
        private Map<String, Object> arguments;
    }
}
