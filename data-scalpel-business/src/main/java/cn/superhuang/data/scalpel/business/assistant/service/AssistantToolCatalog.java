package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolRisk;
import cn.superhuang.data.scalpel.business.assistant.gateway.LlmGateway;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AssistantToolCatalog {

    public static final String UI_NAVIGATE = "ui_navigate";
    public static final String UI_SET_APP_SIDEBAR = "ui_set_app_sidebar";
    public static final String DIRECTORY_LIST_SCOPES = "directory_list_scopes";
    public static final String DIRECTORY_LIST_ROOTS = "directory_list_roots";
    public static final String DIRECTORY_LIST_CHILDREN = "directory_list_children";
    public static final String DIRECTORY_SEARCH = "directory_search";
    public static final String DIRECTORY_GET = "directory_get";
    public static final String DIRECTORY_PREPARE_EXPORT = "directory_prepare_export";
    public static final String DIRECTORY_PROPOSE_CHANGES = "directory_propose_changes";
    public static final String DATA_SOURCE_LIST_TYPES = "datasource_list_types";
    public static final String DATA_SOURCE_SEARCH = "datasource_search";
    public static final String DATA_SOURCE_GET = "datasource_get";
    public static final String DATA_SOURCE_PREPARE_CREATE = "datasource_prepare_create";
    public static final String DATA_SOURCE_PREPARE_UPDATE = "datasource_prepare_update";
    public static final String DATA_SOURCE_PREPARE_TEST = "datasource_prepare_test";

    public List<LlmGateway.ToolDefinition> definitions(Authentication authentication) {
        List<LlmGateway.ToolDefinition> tools = new ArrayList<>();
        List<String> pageKeys = AssistantPageCatalog.available(authentication).stream()
                .map(AssistantPageCatalog.Page::key).toList();
        tools.add(tool(UI_NAVIGATE, "Navigate to a permitted DataScalpel page when the user explicitly asks to open or switch pages.", object(
                Map.of("pageKey", enumString(pageKeys)), List.of("pageKey")
        )));
        tools.add(tool(UI_SET_APP_SIDEBAR, "Expand or collapse the application's main left sidebar.", object(
                Map.of("collapsed", Map.of("type", "boolean")), List.of("collapsed")
        )));
        if (AssistantPageCatalog.hasAuthority(authentication, "directory.view")) {
            tools.add(tool(DIRECTORY_LIST_SCOPES, "List the supported directory business scopes.", object(Map.of(), List.of())));
            tools.add(tool(DIRECTORY_LIST_ROOTS, "List top-level directories for one scope.", scopeSchema(Map.of(), List.of())));
            tools.add(tool(DIRECTORY_LIST_CHILDREN, "List direct child directories of an existing directory.", scopeSchema(
                    Map.of("parentId", uuidSchema()), List.of("parentId")
            )));
            tools.add(tool(DIRECTORY_SEARCH, "Search directories by name or full path inside one scope.", scopeSchema(
                    Map.of(
                            "keyword", Map.of("type", "string", "minLength", 1),
                            "limit", Map.of("type", "integer", "minimum", 1, "maximum", 50)
                    ),
                    List.of("keyword")
            )));
            tools.add(tool(DIRECTORY_GET, "Read one directory's details and full path using a real UUID returned by another directory tool.", object(
                    Map.of("id", uuidSchema()), List.of("id")
            )));
            tools.add(tool(DIRECTORY_PREPARE_EXPORT, "Prepare a client download of the existing directory Excel export for one scope.", scopeSchema(
                    Map.of(), List.of()
            )));
        }
        if (AssistantPageCatalog.hasAuthority(authentication, "directory.manage")) {
            tools.add(tool(
                    DIRECTORY_PROPOSE_CHANGES,
                    "Create a reviewable directory change plan. This never writes directories. Use real UUIDs from read tools for UPDATE and DELETE. UPDATE must contain the complete target state.",
                    directoryChangeSchema()
            ));
        }
        boolean canViewDataSources = AssistantPageCatalog.hasAuthority(authentication, "datasource.view");
        boolean canViewDirectories = AssistantPageCatalog.hasAuthority(authentication, "directory.view");
        if (canViewDataSources) {
            tools.add(tool(
                    DATA_SOURCE_LIST_TYPES,
                    "List safe data-source type capabilities. The result never contains connection addresses or credentials.",
                    object(Map.of(), List.of())
            ));
            tools.add(tool(
                    DATA_SOURCE_SEARCH,
                    "Search registered data sources using safe governance fields only. Never ask for or infer connection details, credentials, SQL, metadata, or sample data.",
                    dataSourceSearchSchema(canViewDirectories)
            ));
            tools.add(tool(
                    DATA_SOURCE_GET,
                    "Read safe basic information for one data source using a real UUID returned by datasource_search. Connection configuration is intentionally unavailable.",
                    object(Map.of("dataSourceId", uuidSchema()), List.of("dataSourceId"))
            ));
        }
        if (canViewDataSources && AssistantPageCatalog.hasAuthority(authentication, "datasource.create")) {
            tools.add(tool(
                    DATA_SOURCE_PREPARE_CREATE,
                    "Prepare a safe data-source create form. This only opens the existing UI and never saves data. Do not include hosts, accounts, credentials, URLs, SQL, options, metadata, or sample data.",
                    dataSourceCreateSchema()
            ));
        }
        if (canViewDataSources && AssistantPageCatalog.hasAuthority(authentication, "datasource.update")) {
            tools.add(tool(
                    DATA_SOURCE_PREPARE_UPDATE,
                    "Prepare the existing edit form for one data source. First call datasource_get, then provide the complete target basic state. Code, type, connection configuration, and credentials cannot be changed here.",
                    dataSourceUpdateSchema()
            ));
        }
        if (canViewDataSources && AssistantPageCatalog.hasAuthority(authentication, "datasource.test")) {
            tools.add(tool(
                    DATA_SOURCE_PREPARE_TEST,
                    "Ask the client to confirm testing one saved data source. This tool never runs the test itself and never exposes diagnostics to the model.",
                    object(Map.of("dataSourceId", uuidSchema()), List.of("dataSourceId"))
            ));
        }
        return List.copyOf(tools);
    }

    public AssistantToolRisk risk(String toolName) {
        return switch (toolName) {
            case UI_NAVIGATE, UI_SET_APP_SIDEBAR, DIRECTORY_PREPARE_EXPORT,
                    DATA_SOURCE_PREPARE_TEST -> AssistantToolRisk.CLIENT_ACTION;
            case DIRECTORY_PROPOSE_CHANGES, DATA_SOURCE_PREPARE_CREATE,
                    DATA_SOURCE_PREPARE_UPDATE -> AssistantToolRisk.WRITE_DRAFT;
            default -> AssistantToolRisk.READ_ONLY;
        };
    }

    private static LlmGateway.ToolDefinition tool(String name, String description, Map<String, Object> parameters) {
        return new LlmGateway.ToolDefinition(name, description, parameters);
    }

    private static Map<String, Object> directoryChangeSchema() {
        Map<String, Object> operationProperties = new LinkedHashMap<>();
        operationProperties.put("type", enumString(List.of("CREATE", "UPDATE", "DELETE")));
        operationProperties.put("ref", Map.of("type", "string", "maxLength", 80));
        operationProperties.put("id", uuidSchema());
        operationProperties.put("parentId", nullableUuidSchema());
        operationProperties.put("parentRef", Map.of("type", List.of("string", "null"), "maxLength", 80));
        operationProperties.put("name", Map.of("type", "string", "maxLength", 100));
        operationProperties.put("sortOrder", Map.of("type", "integer"));
        operationProperties.put("description", Map.of("type", List.of("string", "null"), "maxLength", 500));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scope", enumString(Arrays.stream(DirectoryScope.values()).map(Enum::name).toList()));
        properties.put("summary", Map.of("type", "string", "maxLength", 500));
        properties.put("operations", Map.of(
                "type", "array",
                "minItems", 1,
                "maxItems", 100,
                "items", object(operationProperties, List.of("type"))
        ));
        return object(properties, List.of("scope", "summary", "operations"));
    }

    private static Map<String, Object> dataSourceSearchSchema(boolean includeDirectoryFilter) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "minLength", 1, "maxLength", 100));
        properties.put("type", enumString(Arrays.stream(DataSourceType.values()).map(Enum::name).toList()));
        properties.put("purpose", enumString(Arrays.stream(DataSourcePurpose.values()).map(Enum::name).toList()));
        properties.put("enabled", Map.of("type", "boolean"));
        if (includeDirectoryFilter) properties.put("directoryId", uuidSchema());
        properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", 50));
        return object(properties, List.of());
    }

    private static Map<String, Object> dataSourceCreateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", Map.of(
                "type", "string",
                "pattern", "^[A-Za-z][A-Za-z0-9_]{0,63}$",
                "maxLength", 64
        ));
        properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", 100));
        properties.put("directoryId", nullableUuidSchema());
        properties.put("purposes", dataSourcePurposesSchema());
        properties.put("type", enumString(Arrays.stream(DataSourceType.values()).map(Enum::name).toList()));
        properties.put("enabled", Map.of("type", "boolean"));
        properties.put("description", nullableTextSchema(1000));
        return object(properties, List.of(
                "code", "name", "directoryId", "purposes", "type", "enabled", "description"
        ));
    }

    private static Map<String, Object> dataSourceUpdateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dataSourceId", uuidSchema());
        properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", 100));
        properties.put("directoryId", nullableUuidSchema());
        properties.put("purposes", dataSourcePurposesSchema());
        properties.put("enabled", Map.of("type", "boolean"));
        properties.put("description", nullableTextSchema(1000));
        return object(properties, List.of(
                "dataSourceId", "name", "directoryId", "purposes", "enabled", "description"
        ));
    }

    private static Map<String, Object> dataSourcePurposesSchema() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "uniqueItems", true,
                "items", enumString(Arrays.stream(DataSourcePurpose.values()).map(Enum::name).toList())
        );
    }

    private static Map<String, Object> nullableTextSchema(int maxLength) {
        return Map.of("type", List.of("string", "null"), "maxLength", maxLength);
    }

    private static Map<String, Object> scopeSchema(Map<String, Object> extra, List<String> requiredExtra) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scope", enumString(Arrays.stream(DirectoryScope.values()).map(Enum::name).toList()));
        properties.putAll(extra);
        List<String> required = new ArrayList<>();
        required.add("scope");
        required.addAll(requiredExtra);
        return object(properties, required);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> enumString(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static Map<String, Object> uuidSchema() {
        return Map.of("type", "string", "format", "uuid");
    }

    private static Map<String, Object> nullableUuidSchema() {
        return Map.of("type", List.of("string", "null"), "format", "uuid");
    }
}
