package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceService;
import cn.superhuang.data.scalpel.business.datasource.web.response.ColumnMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetCanvasTableMetadataResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableResponse;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.DataTaskResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Safe, deliberately narrow task and Canvas resource projection for the assistant. */
@Service
public class AssistantTaskCanvasQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_SCHEMA_LIMIT = 100;
    private static final int MAX_SCHEMA_LIMIT = 200;

    private final DataTaskService taskService;
    private final CanvasTaskDefinitionService canvasService;
    private final DataModelService modelService;
    private final FileDatasetService fileDatasetService;
    private final DataSourceService dataSourceService;
    private final AssistantDirectoryQueryService directoryQueryService;
    private final ObjectMapper objectMapper;

    public AssistantTaskCanvasQueryService(
            DataTaskService taskService,
            CanvasTaskDefinitionService canvasService,
            DataModelService modelService,
            FileDatasetService fileDatasetService,
            DataSourceService dataSourceService,
            AssistantDirectoryQueryService directoryQueryService,
            ObjectMapper objectMapper
    ) {
        this.taskService = taskService;
        this.canvasService = canvasService;
        this.modelService = modelService;
        this.fileDatasetService = fileDatasetService;
        this.dataSourceService = dataSourceService;
        this.directoryQueryService = directoryQueryService;
        this.objectMapper = objectMapper;
    }

    public SearchResult<TaskItem> searchTasks(TaskSearchArguments arguments, boolean includeDirectoryPath) {
        TaskSearchArguments resolved = arguments == null
                ? new TaskSearchArguments(null, null, null, null) : arguments;
        int limit = limit(resolved.limit(), MAX_LIMIT);
        List<String> conditions = new ArrayList<>();
        keywordCondition(conditions, resolved.keyword(), List.of("name"));
        if (resolved.status() != null) conditions.add(equals("status", resolved.status().name()));
        if (resolved.type() != null) conditions.add(equals("type", resolved.type().name()));
        PageResponse<DataTaskResponse> page = taskService.search(new SearchRequest(
                join(conditions), 0, limit, "-updatedAt"
        ));
        Map<UUID, String> paths = includeDirectoryPath ? taskDirectoryPaths() : Map.of();
        return result(page, task -> new TaskItem(
                task.id(), task.name(), task.type(), task.status(), task.description(), task.directoryId(),
                paths.get(task.directoryId()), task.definitionConfigured(), task.definitionVersion(), task.updatedAt()
        ));
    }

    public TaskItem task(UUID taskId, boolean includeDirectoryPath) {
        DataTaskResponse task = taskService.get(taskId);
        String path = includeDirectoryPath && task.directoryId() != null
                ? taskDirectoryPaths().get(task.directoryId()) : null;
        return new TaskItem(
                task.id(), task.name(), task.type(), task.status(), task.description(), task.directoryId(), path,
                task.definitionConfigured(), task.definitionVersion(), task.updatedAt()
        );
    }

    public CanvasSummary canvasSummary(UUID taskId) {
        DataTaskResponse task = taskService.get(taskId);
        if (task.type() != TaskType.SPARK_CANVAS) {
            throw conflict("第一版只支持批 Canvas 任务");
        }
        CanvasTaskDefinitionResponse response = canvasService.get(taskId);
        CanvasDefinition definition = response.definition();
        List<NodeSummary> nodes = definition == null || definition.nodes() == null ? List.of()
                : definition.nodes().stream().map(node -> new NodeSummary(
                        node.nodeType().name(), node.name()
                )).toList();
        List<EdgeSummary> edges = definition == null || definition.edges() == null ? List.of()
                : definition.edges().stream().map(edge -> new EdgeSummary(
                        nodeName(definition.nodes(), edge.sourceNodeId()),
                        nodeName(definition.nodes(), edge.targetNodeId())
                )).toList();
        return new CanvasSummary(
                task.id(), response.configured(), response.version(), response.updatedAt(), nodes, edges,
                task.status() == TaskStatus.DRAFT || task.status() == TaskStatus.DISABLED
        );
    }

    public SearchResult<ModelItem> searchModels(ModelSearchArguments arguments) {
        ModelSearchArguments resolved = arguments == null ? new ModelSearchArguments(null, null) : arguments;
        int limit = limit(resolved.limit(), MAX_LIMIT);
        List<String> conditions = new ArrayList<>();
        keywordCondition(conditions, resolved.keyword(), List.of("name", "code"));
        conditions.add(equals("status", DataModelStatus.PUBLISHED.name()));
        PageResponse<DataModelResponse> page = modelService.search(new SearchRequest(
                join(conditions), 0, limit, "-updatedAt"
        ));
        return result(page, model -> new ModelItem(
                model.id(), model.code(), model.name(), model.status(), model.schemaVersion(), model.description()
        ));
    }

    public SafeSchema modelSchema(UUID modelId, Integer requestedLimit) {
        return modelSchema(modelId, schemaLimit(requestedLimit));
    }

    private SafeSchema modelSchema(UUID modelId, int limit) {
        DataModelDetailResponse detail = modelService.get(modelId);
        if (detail.model().status() != DataModelStatus.PUBLISHED) throw conflict("模型尚未发布，不能用于 Canvas 提案");
        List<SafeField> fields = detail.fields().stream().limit(limit).map(this::safeModelField).toList();
        return schema("MODEL", detail.model().id(), null, detail.model().code(), detail.model().name(),
                detail.model().schemaVersion(), fields, detail.fields().size() > limit);
    }

    public SearchResult<FileDatasetItem> searchFileDatasets(FileDatasetSearchArguments arguments) {
        FileDatasetSearchArguments resolved = arguments == null
                ? new FileDatasetSearchArguments(null, null) : arguments;
        int limit = limit(resolved.limit(), MAX_LIMIT);
        List<String> conditions = new ArrayList<>();
        keywordCondition(conditions, resolved.keyword(), List.of("name"));
        PageResponse<FileDatasetResponse> page = fileDatasetService.search(new SearchRequest(
                join(conditions), 0, limit, "-updatedAt"
        ));
        return result(page, dataset -> new FileDatasetItem(
                dataset.id(), dataset.name(), dataset.type().name(), dataset.readyTableCount(), dataset.description()
        ));
    }

    public SearchResult<FileTableItem> searchFileTables(FileTableSearchArguments arguments) {
        if (arguments == null || arguments.fileDatasetId() == null) throw badRequest("必须指定文件数据集");
        int limit = limit(arguments.limit(), MAX_LIMIT);
        List<String> conditions = new ArrayList<>();
        keywordCondition(conditions, arguments.keyword(), List.of("name", "code"));
        PageResponse<FileDatasetTableResponse> page = fileDatasetService.searchTables(
                arguments.fileDatasetId(), new SearchRequest(join(conditions), 0, limit, "-updatedAt")
        );
        FileDatasetResponse dataset = fileDatasetService.get(arguments.fileDatasetId());
        return result(page, table -> new FileTableItem(
                table.id(), dataset.id(), dataset.name(), dataset.type().name(), table.code(), table.name(),
                table.parseStatus()
        ));
    }

    public SafeSchema fileTableSchema(UUID fileDatasetId, UUID tableId, Integer requestedLimit) {
        return fileTableSchema(fileDatasetId, tableId, schemaLimit(requestedLimit));
    }

    private SafeSchema fileTableSchema(UUID fileDatasetId, UUID tableId, int limit) {
        FileDatasetTableResponse table = fileDatasetService.getTable(fileDatasetId, tableId);
        if (table.parseStatus() != FileDatasetParseStatus.READY
                && table.parseStatus() != FileDatasetParseStatus.SCHEMA_READY) {
            throw conflict("文件表尚未就绪，不能用于 Canvas 提案");
        }
        FileDatasetCanvasTableMetadataResponse metadata = fileDatasetService.queryCanvasMetadata(List.of(tableId))
                .tables().stream().filter(item -> item.fileDatasetId().equals(fileDatasetId)).findFirst()
                .orElseThrow(() -> notFound("文件表不存在"));
        List<SafeField> fields = metadata.fields().stream().limit(limit).map(field -> new SafeField(
                field.name(), field.name(), field.platformTypeDefinition(), field.nullable(), false, false, null
        )).toList();
        return schema("FILE_DATASET_TABLE", metadata.fileDatasetId(), metadata.fileDatasetTableId(),
                metadata.code(), metadata.fileDatasetName() + " / " + metadata.name(), null, fields,
                metadata.fields().size() > limit);
    }

    public JdbcTableSearchResult jdbcTables(UUID dataSourceId, String keyword) {
        DataSourceResponse dataSource = requireJdbcDataSource(dataSourceId);
        var response = dataSourceService.listTables(dataSourceId, null, null, normalize(keyword), true);
        List<JdbcTableItem> items = response.tables().stream().map(table -> new JdbcTableItem(
                dataSource.id(), dataSource.name(), table.identifier().table(), table.type(), table.comment()
        )).toList();
        return new JdbcTableSearchResult(items, response.truncated());
    }

    public SafeSchema jdbcTableSchema(UUID dataSourceId, String tableName, Integer requestedLimit) {
        return jdbcTableSchema(dataSourceId, tableName, schemaLimit(requestedLimit));
    }

    private SafeSchema jdbcTableSchema(UUID dataSourceId, String tableName, int limit) {
        DataSourceResponse dataSource = requireJdbcDataSource(dataSourceId);
        String normalizedTable = required(tableName, "JDBC 表名", 512);
        TableMetadataResponse metadata = dataSourceService.readTable(dataSourceId, null, null, normalizedTable);
        Set<String> primaryKeys = metadata.primaryKey() == null
                ? Set.of() : Set.copyOf(metadata.primaryKey().columns());
        Set<String> uniqueColumns = new HashSet<>();
        metadata.uniqueKeys().stream().filter(key -> key.columns().size() == 1)
                .forEach(key -> uniqueColumns.add(key.columns().getFirst()));
        List<SafeField> fields = metadata.columns().stream().limit(limit).map(column -> safeJdbcField(
                column, primaryKeys.contains(column.name()), uniqueColumns.contains(column.name())
        )).toList();
        return schema("JDBC_TABLE", dataSource.id(), null, normalizedTable,
                dataSource.name() + " / " + normalizedTable, null, fields,
                metadata.columns().size() > limit);
    }

    public ResourceFingerprint fingerprint(TaskCanvasPlan.Input input) {
        return fingerprint(fullSchema(input));
    }

    public SafeSchema fullSchema(TaskCanvasPlan.Input input) {
        if (input == null || input.type() == null) throw badRequest("Canvas 输入资源不完整");
        return switch (input.type()) {
            case MODEL -> modelSchema(input.modelId(), Integer.MAX_VALUE);
            case FILE_DATASET_TABLE -> fileTableSchema(
                    input.fileDatasetId(), input.fileDatasetTableId(), Integer.MAX_VALUE
            );
            case JDBC_TABLE -> {
                requirePurpose(input.dataSourceId(), DataSourcePurpose.SOURCE, "JDBC 输入");
                yield jdbcTableSchema(input.dataSourceId(), input.tableName(), Integer.MAX_VALUE);
            }
        };
    }

    public ResourceFingerprint fingerprint(TaskCanvasPlan.Output output) {
        return fingerprint(fullSchema(output));
    }

    public SafeSchema fullSchema(TaskCanvasPlan.Output output) {
        if (output == null || output.type() == null) throw badRequest("Canvas 输出资源不完整");
        return switch (output.type()) {
            case MODEL_OUTPUT -> modelSchema(output.modelId(), Integer.MAX_VALUE);
            case JDBC_OUTPUT -> {
                requirePurpose(output.dataSourceId(), DataSourcePurpose.DISTRIBUTION, "JDBC 输出");
                yield jdbcTableSchema(output.dataSourceId(), output.targetTableName(), Integer.MAX_VALUE);
            }
        };
    }

    private ResourceFingerprint fingerprint(SafeSchema schema) {
        return new ResourceFingerprint(schema.kind(), schema.resourceId(), schema.subResourceId(), schema.code(),
                schema.name(), schema.fingerprint());
    }

    private SafeField safeModelField(DataModelFieldResponse field) {
        return new SafeField(
                field.code(), field.name(), new PlatformTypeDefinition(
                field.fieldType(), field.length(), field.precision(), field.scale(), field.geometry()
        ), field.nullable(), field.primaryKey(), field.primaryKey(), field.description());
    }

    private static SafeField safeJdbcField(ColumnMetadataResponse field, boolean primaryKey, boolean unique) {
        return new SafeField(
                field.name(), field.name(), field.platformTypeDefinition(), field.nullable(), primaryKey, unique,
                field.comment()
        );
    }

    private SafeSchema schema(
            String kind,
            UUID resourceId,
            UUID subResourceId,
            String code,
            String name,
            Integer version,
            List<SafeField> fields,
            boolean truncated
    ) {
        String fingerprint = sha256(Map.of(
                "kind", kind,
                "resourceId", resourceId,
                "subResourceId", subResourceId == null ? "" : subResourceId,
                "version", version == null ? 0 : version,
                "fields", fields.stream().map(field -> new SchemaFingerprintField(
                        field.code(), field.type(), field.nullable(), field.primaryKey(), field.unique()
                )).toList()
        ));
        return new SafeSchema(kind, resourceId, subResourceId, code, name, version, fields, truncated, fingerprint);
    }

    private String sha256(Object value) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(value);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法计算安全 Schema 指纹", exception);
        }
    }

    private DataSourceResponse requireJdbcDataSource(UUID id) {
        DataSourceResponse dataSource = dataSourceService.get(id);
        if (dataSource.connectionKind() != DataSourceConnectionKind.JDBC) throw badRequest("所选数据源不是 JDBC 类型");
        if (!dataSource.enabled()) throw conflict("所选数据源已停用");
        return dataSource;
    }

    private void requirePurpose(UUID id, DataSourcePurpose purpose, String context) {
        DataSourceResponse dataSource = requireJdbcDataSource(id);
        if (!dataSource.purposes().contains(purpose)) {
            throw conflict(context + "数据源不具有 " + purpose.name() + " 用途");
        }
    }

    private Map<UUID, String> taskDirectoryPaths() {
        var snapshot = directoryQueryService.snapshot(
                cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope.TASK
        );
        Map<UUID, String> result = new LinkedHashMap<>();
        snapshot.nodes().values().forEach(node -> result.put(node.id(), node.path()));
        return result;
    }

    private static String nodeName(List<CanvasNodeDefinition> nodes, String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).map(CanvasNodeDefinition::name)
                .findFirst().orElse("未知节点");
    }

    private static <S, T> SearchResult<T> result(PageResponse<S> page, java.util.function.Function<S, T> mapper) {
        return new SearchResult<>(page.content().stream().map(mapper).toList(),
                page.totalElements() > page.content().size(), page.totalElements());
    }

    private static int limit(Integer value, int maximum) {
        return value == null ? DEFAULT_LIMIT : Math.max(1, Math.min(maximum, value));
    }

    private static int schemaLimit(Integer value) {
        return value == null ? DEFAULT_SCHEMA_LIMIT : Math.max(1, Math.min(MAX_SCHEMA_LIMIT, value));
    }

    private static void keywordCondition(List<String> conditions, String keyword, List<String> fields) {
        String normalized = normalize(keyword);
        if (normalized == null) return;
        String escaped = escapeDsl(normalized);
        conditions.add("(" + fields.stream().map(field -> field + ":*\"" + escaped + "\"*")
                .reduce((left, right) -> left + " OR " + right).orElseThrow() + ")");
    }

    private static String equals(String field, String value) {
        return field + ":\"" + escapeDsl(value) + "\"";
    }

    private static String join(List<String> conditions) {
        return conditions.isEmpty() ? null : String.join(" AND ", conditions);
    }

    private static String escapeDsl(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String required(String value, String label, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) throw badRequest(label + "不能为空");
        if (normalized.length() > maxLength) throw badRequest(label + "不能超过 " + maxLength + " 个字符");
        return normalized;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record TaskSearchArguments(String keyword, TaskStatus status, TaskType type, Integer limit) {}
    public record ModelSearchArguments(String keyword, Integer limit) {}
    public record FileDatasetSearchArguments(String keyword, Integer limit) {}
    public record FileTableSearchArguments(UUID fileDatasetId, String keyword, Integer limit) {}
    public record SearchResult<T>(List<T> items, boolean truncated, long total) {}
    public record TaskItem(UUID id, String name, TaskType type, TaskStatus status, String description,
                           UUID directoryId, String directoryPath, boolean definitionConfigured,
                           Integer definitionVersion, Instant updatedAt) {}
    public record CanvasSummary(UUID taskId, boolean configured, int definitionVersion, Instant updatedAt,
                                List<NodeSummary> nodes, List<EdgeSummary> edges, boolean replaceable) {}
    public record NodeSummary(String type, String name) {}
    public record EdgeSummary(String sourceName, String targetName) {}
    public record ModelItem(UUID id, String code, String name, DataModelStatus status, int schemaVersion,
                            String description) {}
    public record FileDatasetItem(UUID id, String name, String type, long readyTableCount, String description) {}
    public record FileTableItem(UUID id, UUID fileDatasetId, String fileDatasetName, String datasetType,
                                String code, String name, FileDatasetParseStatus status) {}
    public record JdbcTableItem(UUID dataSourceId, String dataSourceName, String tableName, String tableType,
                                String description) {}
    public record JdbcTableSearchResult(List<JdbcTableItem> items, boolean truncated) {}
    public record SafeField(String code, String name, PlatformTypeDefinition type, boolean nullable,
                            boolean primaryKey, boolean unique, String description) {}
    public record SafeSchema(String kind, UUID resourceId, UUID subResourceId, String code, String name,
                             Integer version, List<SafeField> fields, boolean truncated, String fingerprint) {}
    public record ResourceFingerprint(String kind, UUID resourceId, UUID subResourceId, String code,
                                      String name, String fingerprint) {}
    private record SchemaFingerprintField(String code, PlatformTypeDefinition type, boolean nullable,
                                          boolean primaryKey, boolean unique) {}
}
