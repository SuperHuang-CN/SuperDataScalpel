package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolStatus;
import cn.superhuang.data.scalpel.business.assistant.gateway.LlmGateway;
import cn.superhuang.data.scalpel.business.assistant.web.request.ProposeDirectoryChangesRequest;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantClientActionResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantToolExecutionService {

    private final AssistantToolCatalog catalog;
    private final AssistantDirectoryQueryService directoryQueryService;
    private final AssistantDataSourceQueryService dataSourceQueryService;
    private final DirectoryChangePlanService directoryChangePlanService;
    private final AssistantRunPersistenceService runPersistenceService;
    private final ObjectMapper objectMapper;

    public AssistantToolExecutionService(
            AssistantToolCatalog catalog,
            AssistantDirectoryQueryService directoryQueryService,
            AssistantDataSourceQueryService dataSourceQueryService,
            DirectoryChangePlanService directoryChangePlanService,
            AssistantRunPersistenceService runPersistenceService,
            ObjectMapper objectMapper
    ) {
        this.catalog = catalog;
        this.directoryQueryService = directoryQueryService;
        this.dataSourceQueryService = dataSourceQueryService;
        this.directoryChangePlanService = directoryChangePlanService;
        this.runPersistenceService = runPersistenceService;
        this.objectMapper = objectMapper;
    }

    public ToolExecution execute(
            UUID runId,
            UUID sessionId,
            String username,
            Authentication authentication,
            LlmGateway.ToolCall toolCall
    ) {
        String arguments = normalizeArguments(toolCall.argumentsJson());
        String auditArguments = safeArgumentsForAudit(toolCall.name(), arguments);
        try {
            ToolValue value = executeValue(sessionId, runId, username, authentication, toolCall.name(), arguments);
            String resultJson = write(Map.of("ok", true, "result", value.response()));
            runPersistenceService.recordTool(
                    runId, toolCall.name(), catalog.risk(toolCall.name()), AssistantToolStatus.SUCCEEDED,
                    auditArguments, resultJson
            );
            return new ToolExecution(resultJson, value.clientAction(), value.changeSet());
        } catch (AccessDeniedException exception) {
            String resultJson = error("PERMISSION_DENIED", "当前用户无权使用该工具");
            runPersistenceService.recordTool(
                    runId, toolCall.name(), catalog.risk(toolCall.name()), AssistantToolStatus.FAILED,
                    auditArguments, resultJson
            );
            return new ToolExecution(resultJson, null, null);
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            String message = exception instanceof ResponseStatusException response && response.getReason() != null
                    ? response.getReason() : exception.getMessage();
            String resultJson = error("INVALID_TOOL_REQUEST", safeMessage(message));
            runPersistenceService.recordTool(
                    runId, toolCall.name(), catalog.risk(toolCall.name()), AssistantToolStatus.FAILED,
                    auditArguments, resultJson
            );
            return new ToolExecution(resultJson, null, null);
        } catch (RuntimeException exception) {
            String resultJson = error("TOOL_FAILED", "工具执行失败");
            runPersistenceService.recordTool(
                    runId, toolCall.name(), catalog.risk(toolCall.name()), AssistantToolStatus.FAILED,
                    auditArguments, resultJson
            );
            return new ToolExecution(resultJson, null, null);
        }
    }

    private ToolValue executeValue(
            UUID sessionId,
            UUID runId,
            String username,
            Authentication authentication,
            String toolName,
            String argumentsJson
    ) {
        JsonNode arguments = readTree(argumentsJson);
        return switch (toolName) {
            case AssistantToolCatalog.UI_NAVIGATE -> ToolValue.action(
                    navigate(authentication, requiredText(arguments, "pageKey"))
            );
            case AssistantToolCatalog.UI_SET_APP_SIDEBAR ->
                    ToolValue.action(AssistantClientActionResponse.sidebar(requiredBoolean(arguments, "collapsed")));
            case AssistantToolCatalog.DIRECTORY_LIST_SCOPES -> {
                requireAuthority(authentication, "directory.view");
                yield ToolValue.response(Arrays.stream(DirectoryScope.values()).map(scope -> Map.of(
                        "scope", scope.name(), "label", scopeLabel(scope)
                )).toList());
            }
            case AssistantToolCatalog.DIRECTORY_LIST_ROOTS -> {
                requireAuthority(authentication, "directory.view");
                yield ToolValue.response(directoryQueryService.roots(requiredScope(arguments)));
            }
            case AssistantToolCatalog.DIRECTORY_LIST_CHILDREN -> {
                requireAuthority(authentication, "directory.view");
                yield ToolValue.response(directoryQueryService.children(
                        requiredScope(arguments), requiredUuid(arguments, "parentId")
                ));
            }
            case AssistantToolCatalog.DIRECTORY_SEARCH -> {
                requireAuthority(authentication, "directory.view");
                Integer limit = arguments.path("limit").canConvertToInt() ? arguments.path("limit").asInt() : null;
                yield ToolValue.response(directoryQueryService.search(
                        requiredScope(arguments), requiredText(arguments, "keyword"), limit
                ));
            }
            case AssistantToolCatalog.DIRECTORY_GET -> {
                requireAuthority(authentication, "directory.view");
                yield ToolValue.response(directoryQueryService.detail(requiredUuid(arguments, "id")));
            }
            case AssistantToolCatalog.DIRECTORY_PREPARE_EXPORT -> {
                requireAuthority(authentication, "directory.view");
                DirectoryScope scope = requiredScope(arguments);
                yield ToolValue.action(AssistantClientActionResponse.export(scope));
            }
            case AssistantToolCatalog.DIRECTORY_PROPOSE_CHANGES -> {
                requireAuthority(authentication, "directory.manage");
                ProposeDirectoryChangesRequest request = read(argumentsJson, ProposeDirectoryChangesRequest.class);
                AssistantChangeSet changeSet = directoryChangePlanService.propose(sessionId, runId, username, request);
                yield ToolValue.changeSet(changeSet, new ChangeSetResult(
                        changeSet.getId(),
                        changeSet.getSummary(),
                        "PENDING",
                        "计划仅已保存，尚未修改目录；必须等待用户在界面中确认。"
                ));
            }
            case AssistantToolCatalog.DATA_SOURCE_LIST_TYPES -> {
                requireAuthority(authentication, "datasource.view");
                yield ToolValue.response(dataSourceQueryService.types());
            }
            case AssistantToolCatalog.DATA_SOURCE_SEARCH -> {
                requireAuthority(authentication, "datasource.view");
                boolean includeDirectories = AssistantPageCatalog.hasAuthority(authentication, "directory.view");
                if (arguments.path("directoryId").isTextual()) requireAuthority(authentication, "directory.view");
                AssistantDataSourceQueryService.SearchArguments request = read(
                        argumentsJson, AssistantDataSourceQueryService.SearchArguments.class
                );
                yield ToolValue.response(dataSourceQueryService.search(request, includeDirectories));
            }
            case AssistantToolCatalog.DATA_SOURCE_GET -> {
                requireAuthority(authentication, "datasource.view");
                yield ToolValue.response(dataSourceQueryService.detail(
                        requiredUuid(arguments, "dataSourceId"),
                        AssistantPageCatalog.hasAuthority(authentication, "directory.view")
                ));
            }
            case AssistantToolCatalog.DATA_SOURCE_PREPARE_CREATE -> {
                requireAuthority(authentication, "datasource.view");
                requireAuthority(authentication, "datasource.create");
                AssistantDataSourceQueryService.CreateDraftArguments request = read(
                        argumentsJson, AssistantDataSourceQueryService.CreateDraftArguments.class
                );
                yield ToolValue.action(AssistantClientActionResponse.createDataSource(
                        dataSourceQueryService.prepareCreate(request)
                ));
            }
            case AssistantToolCatalog.DATA_SOURCE_PREPARE_UPDATE -> {
                requireAuthority(authentication, "datasource.view");
                requireAuthority(authentication, "datasource.update");
                AssistantDataSourceQueryService.UpdateDraftArguments request = read(
                        argumentsJson, AssistantDataSourceQueryService.UpdateDraftArguments.class
                );
                AssistantDataSourceQueryService.PreparedUpdate prepared = dataSourceQueryService.prepareUpdate(request);
                yield ToolValue.action(AssistantClientActionResponse.editDataSource(
                        prepared.dataSourceId(), prepared.dataSourceName(), prepared.draft()
                ));
            }
            case AssistantToolCatalog.DATA_SOURCE_PREPARE_TEST -> {
                requireAuthority(authentication, "datasource.view");
                requireAuthority(authentication, "datasource.test");
                AssistantDataSourceQueryService.DataSourceTarget target = dataSourceQueryService.testTarget(
                        requiredUuid(arguments, "dataSourceId")
                );
                yield ToolValue.action(AssistantClientActionResponse.testDataSource(target.id(), target.name()));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知或当前不可用的助手工具");
        };
    }

    private static AssistantClientActionResponse navigate(Authentication authentication, String pageKey) {
        AssistantPageCatalog.Page page = AssistantPageCatalog.find(pageKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标页面不在导航白名单中"));
        if (!AssistantPageCatalog.allowed(authentication, page)) throw new AccessDeniedException("page denied");
        return AssistantClientActionResponse.navigate(page.key());
    }

    private static void requireAuthority(Authentication authentication, String authority) {
        if (!AssistantPageCatalog.hasAuthority(authentication, authority)) {
            throw new AccessDeniedException("missing authority");
        }
    }

    private JsonNode readTree(String value) {
        try {
            JsonNode result = objectMapper.readTree(value);
            if (result == null || !result.isObject()) throw new IllegalArgumentException("工具参数必须是 JSON 对象");
            return result;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("工具参数不是有效的 JSON 对象", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("工具参数与约定结构不匹配", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法序列化工具结果", exception);
        }
    }

    private String error(String code, String message) {
        return write(Map.of("ok", false, "error", Map.of("code", code, "message", message)));
    }

    private String safeArgumentsForAudit(String toolName, String argumentsJson) {
        Set<String> allowedFields = switch (toolName) {
            case AssistantToolCatalog.DATA_SOURCE_LIST_TYPES -> Set.of();
            case AssistantToolCatalog.DATA_SOURCE_SEARCH -> Set.of(
                    "keyword", "type", "purpose", "enabled", "directoryId", "limit"
            );
            case AssistantToolCatalog.DATA_SOURCE_GET,
                    AssistantToolCatalog.DATA_SOURCE_PREPARE_TEST -> Set.of("dataSourceId");
            case AssistantToolCatalog.DATA_SOURCE_PREPARE_CREATE -> Set.of(
                    "code", "name", "directoryId", "purposes", "type", "enabled", "description"
            );
            case AssistantToolCatalog.DATA_SOURCE_PREPARE_UPDATE -> Set.of(
                    "dataSourceId", "name", "directoryId", "purposes", "enabled", "description"
            );
            default -> null;
        };
        if (allowedFields == null) return argumentsJson;
        try {
            JsonNode source = objectMapper.readTree(argumentsJson);
            if (source == null || !source.isObject()) return "{}";
            Map<String, JsonNode> safe = new LinkedHashMap<>();
            List<String> orderedFields = List.of(
                    "dataSourceId", "code", "name", "directoryId", "purposes", "type", "enabled",
                    "description", "keyword", "purpose", "limit"
            );
            for (String field : orderedFields) {
                if (allowedFields.contains(field) && source.has(field)) safe.put(field, source.get(field));
            }
            return write(safe);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    private static String normalizeArguments(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).isTextual() ? node.path(field).asText().trim() : "";
        if (value.isEmpty()) throw new IllegalArgumentException("参数 " + field + " 不能为空");
        return value;
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        if (!node.path(field).isBoolean()) throw new IllegalArgumentException("参数 " + field + " 必须是布尔值");
        return node.path(field).asBoolean();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        try {
            return UUID.fromString(requiredText(node, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("参数 " + field + " 必须是有效 UUID", exception);
        }
    }

    private static DirectoryScope requiredScope(JsonNode node) {
        try {
            return DirectoryScope.valueOf(requiredText(node, "scope"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("scope 不是受支持的目录作用域", exception);
        }
    }

    private static String scopeLabel(DirectoryScope scope) {
        return switch (scope) {
            case DATA_SOURCE -> "数据源目录";
            case FILE_DATASET -> "文件数据集目录";
            case MODEL -> "模型目录";
            case TASK -> "任务目录";
            case DATA_SERVICE -> "数据服务目录";
            case ASSET -> "业务领域";
        };
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) return "工具参数或业务状态不满足要求";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record ToolExecution(
            String resultJson,
            AssistantClientActionResponse clientAction,
            AssistantChangeSet changeSet
    ) {
    }

    private record ChangeSetResult(
            UUID changeSetId,
            String summary,
            String status,
            String notice
    ) {
    }

    private record ToolValue(
            Object response,
            AssistantClientActionResponse clientAction,
            AssistantChangeSet changeSet
    ) {
        static ToolValue response(Object response) {
            return new ToolValue(response, null, null);
        }

        static ToolValue action(AssistantClientActionResponse action) {
            return new ToolValue(Map.of("accepted", true, "action", action.type()), action, null);
        }

        static ToolValue changeSet(AssistantChangeSet changeSet, Object response) {
            return new ToolValue(response, null, changeSet);
        }
    }
}
