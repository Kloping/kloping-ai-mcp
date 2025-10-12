package top.kloping.core.ai;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求工具类
 * 处理AI工具的注册、调用和参数解析
 *
 * @author github kloping
 * @since 2025/9/19-14:21
 */
@Data
@Accessors(chain = true)
@Slf4j
public class RequestTool {
    private final String type = "function";
    private Function function;

    @Data
    @Accessors(chain = true)
    public static class Function {
        private String name;
        private String description;
        private Parameter parameters;
    }

    @Data
    @Accessors(chain = true)
    public static class Parameter {
        private String type = "object";
        private Map<String, ParameterDesc> properties = new HashMap<>();
        private List<String> required = new ArrayList<>();
    }

    @Data
    public static class ParameterDesc {
        private String description;
        private String title;
        private String type;
        @JSONField(name = "default")
        private Object defaultValue;
    }

    public static final Map<Object, List<RequestTool>> TOOLS_MAP = new ConcurrentHashMap<>();
    public static final Map<String, Map.Entry<Object, Method>> NAME_2_METHOD = new ConcurrentHashMap<>();

    public static String getTypeByJava(Class<?> type) {
        if (type.isAssignableFrom(String.class)) {
            return "string";
        } else if (type.isAssignableFrom(Integer.class) || type.isAssignableFrom(int.class)) {
            return "integer";
        } else if (type.isAssignableFrom(Number.class) || type.isAssignableFrom(float.class) || type.isAssignableFrom(double.class)) {
            return "number";
        } else if (type.isAssignableFrom(Boolean.class) || type.isAssignableFrom(boolean.class)) {
            return "boolean";
        } else if (type.isAssignableFrom(Collection.class) || type.isArray()) {
            return "array";
        } else {
            return "object";
        }
    }
}
