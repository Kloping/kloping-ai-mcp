package top.kloping.core.ai.dto;


/**
 *
 * @author github kloping
 * @since 2025/9/20-11:14
 */
public class InitializeRequest extends McpReqPack<McpReqPack.Params> {

    public InitializeRequest(String id, Params params) {
        super(id, "initialize", params);
    }

    public InitializeRequest() {
        super("", "initialize", new Params());
    }
}
