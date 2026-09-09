package cn.superhuang.data.scalpel.business.systemmcp.service;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import java.util.*;
/** Retains reachable local references, including recursive DTOs, rather than expanding them. */
@Component
public class SystemMcpContractBuilder {
    private final ObjectMapper mapper;
    private final SystemMcpSchemaService schemas;
    public SystemMcpContractBuilder(ObjectMapper mapper, SystemMcpSchemaService schemas) {
        this.mapper = mapper;
        this.schemas = schemas;
    }
    public ObjectNode build(JsonNode document, JsonNode pathItem, JsonNode operation) {
        ObjectNode contract = mapper.createObjectNode();
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        for (JsonNode source : List.of(pathItem.path("parameters"), operation.path("parameters"))) {
            for (JsonNode raw : source) {
                JsonNode p = dereference(raw, document);
                if (!List.of("path", "query").contains(p.path("in").asText())) throw new IllegalArgumentException("请求头和 Cookie 参数需要适配");
                parameters.put(p.path("in").asText() + ":" + p.path("name").asText(), p.deepCopy());
            }
        }
        ArrayNode list = contract.putArray("parameters");
        parameters.values().forEach(list::add);
        if (operation.has("requestBody")) contract.set("requestBody", dereference(operation.get("requestBody"), document).deepCopy());
        ObjectNode responses = contract.putObject("responses");
        for (String code : operation.path("responses").propertyNames()) {
            JsonNode response = dereference(operation.path("responses").get(code), document);
            if (code.startsWith("2")) {
                for (String media : response.path("content").propertyNames()) {
                    if (!media.equals("*/*") && !media.equals("application/json") && !(media.startsWith("application/") && media.endsWith("+json")))
                        throw new IllegalArgumentException("第一版仅支持 JSON 和空响应");
                }
            }
            responses.set(code, response.deepCopy());
        }
        ObjectNode components = mapper.createObjectNode();
        collectReferences(contract, document, components, new HashSet<>());
        contract.set("components", components);
        normalize(contract);
        ObjectNode input = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = input.putObject("properties");
        ArrayNode required = input.putArray("required");
        for (String location : List.of("path", "query")) {
            ObjectNode group = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
            ObjectNode fields = group.putObject("properties");
            ArrayNode mandatory = group.putArray("required");
            for (JsonNode parameter : contract.path("parameters")) {
                if (!location.equals(parameter.path("in").asText())) continue;
                String name = parameter.path("name").asText();
                if (!parameter.has("schema")) throw new IllegalArgumentException("参数缺少 Schema");
                if (location.equals("path") && !parameter.path("style").asText("simple").equals("simple")) throw new IllegalArgumentException("路径参数序列化需要适配");
                if (location.equals("query") && (!parameter.path("style").asText("form").equals("form")))
                throw new IllegalArgumentException("查询参数序列化需要适配");
                JsonNode parameterSchema = dereference(parameter.get("schema"), document);
                String type = parameterSchema.path("type").asText();
                if (type.equals("object") || type.equals("array") && (!location.equals("query") || dereference(parameterSchema.path("items"), document).path("type").asText().matches("object|array")))
                throw new IllegalArgumentException("路径或查询参数结构需要适配");
                fields.set(name, parameter.get("schema").deepCopy());
                if (location.equals("path") || parameter.path("required").asBoolean()) mandatory.add(name);
            }
            String key = location.equals("path") ? "pathParams" : "queryParams";
            properties.set(key, group);
            if (!mandatory.isEmpty()) required.add(key);
        }
        JsonNode body = contract.path("requestBody");
        if (!body.isMissingNode()) {
            JsonNode content = body.path("content");
            JsonNode json = jsonContent(content);
            if (json == null || !json.has("schema")) throw new IllegalArgumentException("仅支持有 Schema 的 JSON 请求体");
            properties.set("body", json.get("schema").deepCopy());
            if (body.path("required").asBoolean()) required.add("body");
        }
        input.set("components", components.deepCopy());
        normalize(input);
        schemas.check(input);
        contract.set("inputSchema", input);
        return contract;
    }
    public static JsonNode jsonContent(JsonNode content) {
        for (String key : content.propertyNames())
        if (key.equals("application/json") || key.startsWith("application/") && key.endsWith("+json")) return content.get(key);
        return null;
    }
    private JsonNode dereference(JsonNode node, JsonNode document) {
        Set<String> seen = new HashSet<>();
        while (node.has("$ref")) {
            String ref = node.path("$ref").asText();
            if (!ref.startsWith("#/components/") || !seen.add(ref)) throw new IllegalArgumentException("接口引用无效");
            node = document.at(ref.substring(1));
            if (node.isMissingNode()) throw new IllegalArgumentException("接口引用不存在");
        }
        return node;
    }
    private void collectReferences(JsonNode node, JsonNode document, ObjectNode target, Set<String> seen) {
        schemas.rejectRemoteReferences(node);
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText();
                if (!ref.startsWith("#/components/")) throw new IllegalArgumentException("仅支持本地 components 引用");
                if (seen.add(ref)) {
                    JsonNode value = document.at(ref.substring(1));
                    if (value.isMissingNode()) throw new IllegalArgumentException("Schema 引用不存在");
                    String[] parts = ref.substring("#/components/".length()).split("/", 2);
                    if (parts.length != 2 || parts[1].contains("/")) throw new IllegalArgumentException("Schema 引用路径需要适配");
                    String name = parts[1].replace("~1", "/").replace("~0", "~");
                    ObjectNode group = target.has(parts[0]) ? (ObjectNode) target.get(parts[0]) : target.putObject(parts[0]);
                    group.set(name, value.deepCopy());
                    collectReferences(value, document, target, seen);
                }
            }
            for (String key : node.propertyNames()) collectReferences(node.get(key), document, target, seen);
        } else if (node.isArray()) for (JsonNode child : node) collectReferences(child, document, target, seen);
    }
    private void normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.path("format").asText().equals("binary") || object.path("format").asText().equals("byte"))
                throw new IllegalArgumentException("第一版不支持二进制 Schema");
            for (String key : new ArrayList<>(object.propertyNames())) {
                if (!Set.of("example", "examples", "default", "enum", "const").contains(key)) normalize(object.get(key));
            }
            if (object.path("nullable").asBoolean()) {
                object.remove("nullable");
                ObjectNode original = object.deepCopy();
                object.removeAll();
                object.putArray("anyOf").add(original).add(mapper.createObjectNode().put("type", "null"));
            }
            for (String bound : List.of("Minimum", "Maximum")) {
                String exclusive = "exclusive" + bound;
                if (object.path(exclusive).isBoolean()) {
                    boolean enabled = object.get(exclusive).asBoolean();
                    object.remove(exclusive);
                    String ordinary = bound.toLowerCase(Locale.ROOT);
                    if (enabled && object.has(ordinary)) object.set(exclusive, object.remove(ordinary));
                }
            }
        } else if (node.isArray()) for (JsonNode child : node) normalize(child);
    }
}
