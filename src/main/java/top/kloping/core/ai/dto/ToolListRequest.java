package top.kloping.core.ai.dto;


/**
 * @author github kloping
 * @since 2025/9/20-11:24
 */
public class ToolListRequest extends McpReqPack<McpReqPack.Params> {
    public ToolListRequest(String id, Params params) {
        super(id, "tools/list", params);
    }
}
