package cn.superhuang.data.scalpel.business.systemmcp.service;
import com.networknt.schema.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
/** Independent schema boundary; no dependency on the online Groovy MCP platform. */
@Service
public class SystemMcpSchemaService {
    private final Map<String, Schema> cache = new LinkedHashMap<>();
    private synchronized Schema compile(JsonNode schema) {
        String key = schema.toString();
        Schema value = cache.get(key);
        if (value != null) return value;
        rejectRemoteReferences(schema);
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
        b -> b.schemaCacheEnabled(false).schemaLoader(l -> l.fetchRemoteResources(false)));
        value = registry.getSchema(key, InputFormat.JSON);
        value.initializeValidators();
        cache.put(key, value);
        while (cache.size() > 128) cache.remove(cache.keySet().iterator().next());
        return value;
    }
    public void check(JsonNode schema) {
        compile(schema);
    }
    public void validate(JsonNode schema, JsonNode arguments) {
        var errors = compile(schema).validate(arguments.toString(), InputFormat.JSON);
        if (!errors.isEmpty()) {
            var exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数不符合当前接口契约，请检查校验问题并重新获取接口详情");
            exception.getBody().setProperty("code", "SYSTEM_MCP_ARGUMENT_INVALID");
            exception.getBody().setProperty("violations", errors.stream().limit(50).map(error -> Map.of(
            "field", error.getInstanceLocation().toString(), "message", "不符合 " + error.getKeyword() + " 约束")).toList());
            throw exception;
        }
    }
    public void rejectRemoteReferences(JsonNode node) {
        if (node.isObject()) {
            for (String key : node.propertyNames()) {
                JsonNode value = node.get(key);
                if ((key.equals("$ref") || key.equals("$dynamicRef") || key.equals("$recursiveRef"))
                && value.isTextual() && !value.asText().startsWith("#/")) {
                    throw new IllegalArgumentException("不支持远程 Schema 引用");
                }
                rejectRemoteReferences(value);
            }
        } else if (node.isArray()) node.forEach(this::rejectRemoteReferences);
    }
}
