package cn.superhuang.data.scalpel.business.mcp.service;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class McpSchemaService {
    private static final int CACHE_LIMIT = 128;
    private final ObjectMapper objectMapper;
    private final Map<String, Schema> compiled = new LinkedHashMap<>(16, .75f, true);

    public McpSchemaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseSchema(String json, boolean required) {
        if (json == null || json.isBlank()) {
            if (required) throw bad("Schema 不能为空");
            return null;
        }
        try {
            JsonNode schema = objectMapper.readTree(json);
            if (!schema.isObject() || !"object".equals(schema.path("type").asText())) {
                throw bad("MCP Schema 的 type 必须为 object");
            }
            rejectRemoteRefs(schema);
            compile(json);
            return schema;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw bad("Schema 不是合法的 JSON Schema，请检查类型、约束及引用");
        }
    }

    private synchronized Schema compile(String json) {
        Schema cached = compiled.get(json);
        if (cached != null) return cached;
        JsonNode node = objectMapper.readTree(json);
        String dialect = node.path("$schema").asText(SpecificationVersion.DRAFT_2020_12.getDialectId());
        SpecificationVersion version = SpecificationVersion.fromDialectId(dialect)
                .orElseThrow(() -> bad("不支持该 JSON Schema 方言"));
        // Uses the validator already brought in by the MCP SDK. Meta schemas are bundled;
        // external fetching stays disabled, including on an isolated network.
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(version,
                builder -> builder.schemaCacheEnabled(false).schemaLoader(loader -> loader.fetchRemoteResources(false)));
        Schema meta = registry.getSchema(SchemaLocation.of(version.getDialectId()));
        if (!meta.validate(json, InputFormat.JSON).isEmpty()) throw bad("Schema 定义不合法，请检查字段类型和约束");
        Schema result = registry.getSchema(json, InputFormat.JSON);
        result.initializeValidators();
        compiled.put(json, result);
        while (compiled.size() > CACHE_LIMIT) compiled.remove(compiled.keySet().iterator().next());
        return result;
    }

    public JsonNode parseArguments(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            if (!value.isObject()) throw bad("Tool 参数必须是 JSON 对象");
            return value;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw bad("Tool 参数不是有效的 JSON");
        }
    }

    public void validate(JsonNode schema, JsonNode value, String label) {
        if (schema == null) return;
        if (!compile(schema.toString()).validate(value.toString(), InputFormat.JSON).isEmpty()) {
            // Validator diagnostics can contain instance values. Never propagate them to audit.
            throw bad(label + "不符合 Schema");
        }
    }

    private void rejectRemoteRefs(JsonNode node) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                JsonNode child = node.get(name);
                if (("$ref".equals(name) || "$dynamicRef".equals(name) || "$recursiveRef".equals(name))
                        && child.isTextual() && !child.asText().startsWith("#")) {
                    throw bad("暂不支持远程 Schema 引用");
                }
                rejectRemoteRefs(child);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) rejectRemoteRefs(child);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
