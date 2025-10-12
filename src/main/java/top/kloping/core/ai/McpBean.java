package top.kloping.core.ai;

import lombok.Data;
import org.springframework.ai.tool.ToolCallback;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * create on 16:51
 *
 * @author github kloping
 * @since 2025/10/12
 */
@Data
public class McpBean {

    private final Map<String, McpOne> mcpOnes = new HashMap<>();

    // 获取所有配置的Mcp
    public List<McpOne> getMcpList() {
        return new LinkedList<>(mcpOnes.values());
    }

    @Data
    public static class McpOne {
        // 配置中的key
        private String name;

        // key: toolName
        // value: toolCallback
        private Map<String, ToolCallback> toolCallbacks;

        public ToolCallback[] getToolCallbacks() {
            return toolCallbacks.values().toArray(ToolCallback[]::new);
        }
    }

    /**
     * 获取所有工具
     *
     * @return 用于AI调用配置
     */
    public ToolCallback[] getToolCallbacks() {
        return mcpOnes.values().stream().flatMap(mcpOne -> mcpOne.toolCallbacks.values().stream()).toArray(ToolCallback[]::new);
    }
}
