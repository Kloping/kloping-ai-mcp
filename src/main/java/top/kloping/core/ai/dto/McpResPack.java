package top.kloping.core.ai.dto;

import lombok.Data;

/**
 *
 * "jsonrpc": "2.0",
 * "id": 2,
 * "result":
 *
 * @author github kloping
 * @since 2025/9/20-11:43
 */
@Data
public class McpResPack<T> {
    private String jsonrpc = "2.0";
    private String id;
    private T result;

    public McpResPack(String jsonrpc, String id, T result) {
        this.jsonrpc = jsonrpc;
        this.id = id;
        this.result = result;
    }
}
