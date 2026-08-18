package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantDataSourceDraftResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantDataSourceDraftMode;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceService;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceTypeResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AssistantDataSourceQueryService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final DataSourceService dataSourceService;
    private final DataSourceRuntimeService runtimeService;
    private final DirectoryService directoryService;
    private final AssistantDirectoryQueryService directoryQueryService;

    public AssistantDataSourceQueryService(
            DataSourceService dataSourceService,
            DataSourceRuntimeService runtimeService,
            DirectoryService directoryService,
            AssistantDirectoryQueryService directoryQueryService
    ) {
        this.dataSourceService = dataSourceService;
        this.runtimeService = runtimeService;
        this.directoryService = directoryService;
        this.directoryQueryService = directoryQueryService;
    }

    public List<DataSourceTypeItem> types() {
        return runtimeService.dataSourceTypes().stream()
                .map(AssistantDataSourceQueryService::safeType)
                .sorted(Comparator.comparing(DataSourceTypeItem::displayName))
                .toList();
    }

    public DataSourceSearchResult search(SearchArguments arguments, boolean includeDirectoryDetails) {
        SearchArguments resolved = arguments == null ? new SearchArguments(null, null, null, null, null, null) : arguments;
        int limit = resolved.limit() == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, resolved.limit()));
        List<UUID> directoryIds = resolved.directoryId() == null
                ? List.of()
                : descendantDirectoryIds(resolved.directoryId(), includeDirectoryDetails);
        String search = searchExpression(resolved, directoryIds);
        PageResponse<DataSourceResponse> page = dataSourceService.search(new SearchRequest(search, 0, limit, "-updatedAt"));
        DirectoryContext directories = includeDirectoryDetails ? directoryContext() : DirectoryContext.empty();
        return new DataSourceSearchResult(
                page.content().stream().map(item -> safeDataSource(item, directories)).toList(),
                page.totalElements() > page.content().size(),
                page.totalElements()
        );
    }

    public DataSourceItem detail(UUID id, boolean includeDirectoryDetails) {
        DataSourceResponse response = dataSourceService.get(id);
        return safeDataSource(response, includeDirectoryDetails ? directoryContext() : DirectoryContext.empty());
    }

    public AssistantDataSourceDraftResponse prepareCreate(CreateDraftArguments arguments) {
        if (arguments == null) throw badRequest("数据源新建草稿不能为空");
        String code = requiredCode(arguments.code());
        String name = requiredText(arguments.name(), "数据源名称", 100);
        DataSourceType type = requireType(arguments.type());
        Set<DataSourcePurpose> purposes = validPurposes(type, arguments.purposes());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, arguments.directoryId());
        return new AssistantDataSourceDraftResponse(
                AssistantDataSourceDraftMode.CREATE,
                code,
                name,
                arguments.directoryId(),
                purposes,
                type,
                arguments.enabled() == null || arguments.enabled(),
                optionalText(arguments.description(), "数据源说明", 1000)
        );
    }

    public PreparedUpdate prepareUpdate(UpdateDraftArguments arguments) {
        if (arguments == null || arguments.dataSourceId() == null) throw badRequest("必须指定要修改的数据源");
        DataSourceResponse current = dataSourceService.get(arguments.dataSourceId());
        String name = requiredText(arguments.name(), "数据源名称", 100);
        Set<DataSourcePurpose> purposes = validPurposes(current.type(), arguments.purposes());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, arguments.directoryId());
        AssistantDataSourceDraftResponse draft = new AssistantDataSourceDraftResponse(
                AssistantDataSourceDraftMode.UPDATE,
                null,
                name,
                arguments.directoryId(),
                purposes,
                null,
                requireBoolean(arguments.enabled(), "启用状态"),
                optionalText(arguments.description(), "数据源说明", 1000)
        );
        return new PreparedUpdate(current.id(), current.name(), draft);
    }

    public DataSourceTarget testTarget(UUID id) {
        DataSourceResponse current = dataSourceService.get(id);
        return new DataSourceTarget(current.id(), current.name());
    }

    private List<UUID> descendantDirectoryIds(UUID directoryId, boolean includeDirectoryDetails) {
        if (!includeDirectoryDetails) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户无权按目录查询数据源");
        AssistantDirectoryQueryService.DirectorySnapshot snapshot = directoryQueryService.snapshot(DirectoryScope.DATA_SOURCE);
        AssistantDirectoryQueryService.SnapshotNode root = snapshot.nodes().get(directoryId);
        if (root == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源目录不存在");
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        appendDirectoryIds(snapshot, root, result);
        return List.copyOf(result);
    }

    private static void appendDirectoryIds(
            AssistantDirectoryQueryService.DirectorySnapshot snapshot,
            AssistantDirectoryQueryService.SnapshotNode node,
            Set<UUID> result
    ) {
        if (!result.add(node.id())) return;
        for (UUID childId : node.childIds()) {
            AssistantDirectoryQueryService.SnapshotNode child = snapshot.nodes().get(childId);
            if (child != null) appendDirectoryIds(snapshot, child, result);
        }
    }

    private DirectoryContext directoryContext() {
        AssistantDirectoryQueryService.DirectorySnapshot snapshot = directoryQueryService.snapshot(DirectoryScope.DATA_SOURCE);
        return new DirectoryContext(snapshot);
    }

    private static DataSourceItem safeDataSource(DataSourceResponse response, DirectoryContext directories) {
        String path = response.directoryId() == null ? null : directories.path(response.directoryId());
        return new DataSourceItem(
                response.id(), response.code(), response.name(), response.type(), response.connectionKind().name(),
                response.purposes(), response.enabled(), response.description(), response.directoryId(), path,
                response.createdAt(), response.updatedAt()
        );
    }

    private static DataSourceTypeItem safeType(DataSourceTypeResponse response) {
        return new DataSourceTypeItem(
                response.id(), response.displayName(), response.connectionKind(), response.supportedPurposes(),
                response.driverAvailable(), response.connectionTestAvailable()
        );
    }

    private static String searchExpression(SearchArguments arguments, List<UUID> directoryIds) {
        List<String> conditions = new ArrayList<>();
        String keyword = normalizeOptional(arguments.keyword());
        if (keyword != null) {
            String escaped = escapeDsl(keyword);
            conditions.add("(name:*\"" + escaped + "\"* OR code:*\"" + escaped + "\"*)");
        }
        if (arguments.type() != null) conditions.add(equals("type", arguments.type().name()));
        if (arguments.purpose() != null) {
            String field = switch (arguments.purpose()) {
                case SOURCE -> "sourceEnabled";
                case STORAGE -> "storageEnabled";
                case DISTRIBUTION -> "distributionEnabled";
            };
            conditions.add(equals(field, "true"));
        }
        if (arguments.enabled() != null) conditions.add(equals("enabled", arguments.enabled().toString()));
        if (!directoryIds.isEmpty()) {
            conditions.add(directoryIds.size() == 1
                    ? equals("directoryId", directoryIds.getFirst().toString())
                    : "(" + directoryIds.stream().map(id -> equals("directoryId", id.toString()))
                    .reduce((left, right) -> left + " OR " + right).orElseThrow() + ")");
        }
        return conditions.isEmpty() ? null : String.join(" AND ", conditions);
    }

    private static String equals(String field, String value) {
        return field + ":\"" + escapeDsl(value) + "\"";
    }

    private static String escapeDsl(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static DataSourceType requireType(DataSourceType type) {
        if (type == null) throw badRequest("数据源类型不能为空");
        return type;
    }

    private static Set<DataSourcePurpose> validPurposes(DataSourceType type, Set<DataSourcePurpose> purposes) {
        if (purposes == null || purposes.isEmpty()) throw badRequest("数据源用途不能为空");
        if (purposes.stream().anyMatch(java.util.Objects::isNull)) throw badRequest("数据源用途无效");
        if (!type.supportedPurposes().containsAll(purposes)) {
            throw badRequest("数据源类型不支持所选用途");
        }
        return Set.copyOf(purposes);
    }

    private static String requiredCode(String value) {
        String normalized = requiredText(value, "数据源编码", 64);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw badRequest("数据源编码只能包含字母、数字和下划线，且必须以字母开头");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String requiredText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw badRequest(label + "不能为空");
        if (normalized.length() > maxLength) throw badRequest(label + "不能超过 " + maxLength + " 个字符");
        return normalized;
    }

    private static String optionalText(String value, String label, int maxLength) {
        String normalized = normalizeOptional(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw badRequest(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean requireBoolean(Boolean value, String label) {
        if (value == null) throw badRequest(label + "不能为空");
        return value;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record SearchArguments(
            String keyword,
            DataSourceType type,
            DataSourcePurpose purpose,
            Boolean enabled,
            UUID directoryId,
            Integer limit
    ) {
    }

    public record CreateDraftArguments(
            String code,
            String name,
            UUID directoryId,
            Set<DataSourcePurpose> purposes,
            DataSourceType type,
            Boolean enabled,
            String description
    ) {
    }

    public record UpdateDraftArguments(
            UUID dataSourceId,
            String name,
            UUID directoryId,
            Set<DataSourcePurpose> purposes,
            Boolean enabled,
            String description
    ) {
    }

    public record DataSourceTypeItem(
            String id,
            String displayName,
            String connectionKind,
            Set<String> supportedPurposes,
            boolean driverAvailable,
            boolean connectionTestAvailable
    ) {
    }

    public record DataSourceItem(
            UUID id,
            String code,
            String name,
            DataSourceType type,
            String connectionKind,
            Set<DataSourcePurpose> purposes,
            boolean enabled,
            String description,
            UUID directoryId,
            String directoryPath,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DataSourceSearchResult(
            List<DataSourceItem> items,
            boolean truncated,
            long total
    ) {
    }

    public record PreparedUpdate(
            UUID dataSourceId,
            String dataSourceName,
            AssistantDataSourceDraftResponse draft
    ) {
    }

    public record DataSourceTarget(UUID id, String name) {
    }

    private record DirectoryContext(AssistantDirectoryQueryService.DirectorySnapshot snapshot) {
        static DirectoryContext empty() {
            return new DirectoryContext(null);
        }

        String path(UUID id) {
            if (snapshot == null) return null;
            AssistantDirectoryQueryService.SnapshotNode node = snapshot.nodes().get(id);
            return node == null ? null : node.path();
        }
    }
}
