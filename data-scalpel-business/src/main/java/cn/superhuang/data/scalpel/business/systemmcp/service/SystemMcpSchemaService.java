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
        rejectNonProgressingCycles(schema);
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

    /** Property/item recursion consumes input; ref/composition recursion at the same value cannot terminate. */
    private void rejectNonProgressingCycles(JsonNode root) {
        var active = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<JsonNode, Boolean>());
        var done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<JsonNode, Boolean>());
        visitSchemas(root, node -> visitSameValue(node, root, active, done));
    }

    /** Visit schema values, never property-name maps or example/default payloads, children first. */
    static void visitSchemas(JsonNode node, java.util.function.Consumer<JsonNode> visitor) {
        if (!node.isObject()) return;
        for (String key : java.util.List.of("properties", "patternProperties", "$defs", "definitions", "dependentSchemas")) {
            for (JsonNode child : node.path(key)) visitSchemas(child, visitor);
        }
        for (String key : java.util.List.of("allOf", "oneOf", "anyOf", "prefixItems")) {
            for (JsonNode child : node.path(key)) visitSchemas(child, visitor);
        }
        for (String key : java.util.List.of("items", "additionalItems", "contains", "additionalProperties",
                "unevaluatedProperties", "unevaluatedItems", "propertyNames", "not", "if", "then", "else", "contentSchema")) {
            JsonNode child = node.path(key);
            if (child.isArray()) child.forEach(item -> visitSchemas(item, visitor));
            else visitSchemas(child, visitor);
        }
        for (JsonNode child : node.path("components").path("schemas")) visitSchemas(child, visitor);
        visitor.accept(node);
    }

    private void visitSameValue(JsonNode node, JsonNode root, java.util.Set<JsonNode> active, java.util.Set<JsonNode> done) {
        if (!node.isObject() || done.contains(node)) return;
        if (!active.add(node)) throw new IllegalArgumentException("Schema 包含未进入子字段的循环引用，需要修复多态契约");
        if (node.path("$ref").isTextual()) {
            var target = root.at(node.path("$ref").asText().substring(1));
            if (target.isMissingNode()) throw new IllegalArgumentException("Schema 本地引用不存在");
            visitSameValue(target, root, active, done);
        }
        for (String key : java.util.List.of("allOf", "oneOf", "anyOf")) {
            for (JsonNode branch : node.path(key)) visitSameValue(branch, root, active, done);
        }
        for (String key : java.util.List.of("not", "if", "then", "else")) {
            visitSameValue(node.path(key), root, active, done);
        }
        for (JsonNode dependency : node.path("dependentSchemas")) visitSameValue(dependency, root, active, done);
        active.remove(node);
        done.add(node);
    }
}
