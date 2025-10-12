package top.kloping.core.ai.dto;


/**
 * @author github kloping
 * @since 2025/9/20-11:24
 */
public class ToolListRequest extends McpReqPack<McpReqPack.Params> {
    public ToolListRequest(Integer id, Params params) {
        super(id, "tools/list", params);
    }
}
