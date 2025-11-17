### kloping-ai-mcp

```xml

<dependency>
    <groupId>top.kloping.core</groupId>
    <artifactId>kloping-ai-mcp</artifactId>
    <!--根据版本更新-->
    <version>0.0.1</version>
</dependency>

```

- 支持jdk17+ (因为spring-ai最低支持)


> 配置示例

```yaml
top.kloping.ai.mcp:
  servers:
    # 自定义 mcp server名字
    weather-service:
      # 服务 地址
      server: https://dashscope.aliyuncs.com
      # 接入点
      endpoint: /api/v1/mcps/WebSearch/sse
      # 密钥
      token: sk-xxx
```

> 代码使用

```java
import org.springframework.stereotype.Service;
import top.kloping.core.ai.McpBean;

@Service
public class ChatOneService {
    private final ChatClient.Builder builder;

    public ChatOneService(ChatClient.Builder builder) {
        this.builder = builder;
    }

    // 自动注入封装好的Bean
    @Autowired
    McpBean mcpBean;

    @Bean
    public ChatClient chatClient() {
        // 将 从McpServer加载的 Tool 配置到 模型
        return builder.defaultToolCallbacks(
                mcpBean.getToolCallbacks()
        ).build();
    }

}
```