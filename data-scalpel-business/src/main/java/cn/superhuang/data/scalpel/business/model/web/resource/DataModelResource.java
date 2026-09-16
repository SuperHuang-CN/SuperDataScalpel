package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.lineage.service.ModelLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageFieldGraphResponse;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.service.DataModelReferenceQueryService;
import cn.superhuang.data.scalpel.business.model.service.DataModelPhysicalStatisticsService;
import cn.superhuang.data.scalpel.business.model.service.DataModelSpatialPreviewService;
import cn.superhuang.data.scalpel.business.model.service.SpatialPreviewImage;
import cn.superhuang.data.scalpel.business.model.service.ModelMetadataExcelFile;
import cn.superhuang.data.scalpel.business.model.service.ModelMetadataExcelService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreateManagedDraftRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreatePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ExecutePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ExportModelMetadataRequest;
import cn.superhuang.data.scalpel.business.model.web.request.FileDatasetImportPreviewRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ImportModelMetadataRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ManagedImportPreviewRequest;
import cn.superhuang.data.scalpel.business.model.web.request.QueryModelFieldLineageRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalChangeResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalStatisticsResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelSpatialPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelReferencesResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableDdlPlanResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableInspectionResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.FileDatasetImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PlatformTypeCapabilityResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ManagedImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportResultResponse;
import cn.superhuang.data.scalpel.business.task.service.TaskModelRelationQueryService;
import cn.superhuang.data.scalpel.business.task.web.response.ModelRelatedTaskResponse;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskRelationRole;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "模型管理")
public class DataModelResource {

    private final DataModelService service;
    private final DataModelPhysicalStatisticsService physicalStatisticsService;
    private final DataModelSpatialPreviewService spatialPreviewService;
    private final ModelMetadataExcelService metadataExcelService;
    private final TaskModelRelationQueryService taskModelRelationQueryService;
    private final ModelLineageQueryService lineageQueryService;
    private final DataModelReferenceQueryService referenceQueryService;

    public DataModelResource(
            DataModelService service,
            DataModelPhysicalStatisticsService physicalStatisticsService,
            DataModelSpatialPreviewService spatialPreviewService,
            ModelMetadataExcelService metadataExcelService,
            TaskModelRelationQueryService taskModelRelationQueryService,
            ModelLineageQueryService lineageQueryService,
            DataModelReferenceQueryService referenceQueryService
    ) {
        this.service = service;
        this.physicalStatisticsService = physicalStatisticsService;
        this.spatialPreviewService = spatialPreviewService;
        this.metadataExcelService = metadataExcelService;
        this.taskModelRelationQueryService = taskModelRelationQueryService;
        this.lineageQueryService = lineageQueryService;
        this.referenceQueryService = referenceQueryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型")
    @GetMapping
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型", description = "分页查询模型基本信息、类型、生命周期和物理表摘要，不返回完整字段列表或业务数据。")
    public PageResponse<DataModelResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型详情和字段")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型详情和字段", description = "读取模型元数据、完整字段、生命周期、物理定位和当前结构状态，不读取物理表数据。")
    public DataModelDetailResponse get(@Parameter(description = "模型 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询阻止模型删除或字段变更的保护性引用",
            prerequisites = "模型必须存在；deletable 只判断引用，不判断模型生命周期。缺少 metric.view 时可能看不到具体指标，但指标引用仍会阻止删除。",
            relatedOperations = {"GET /api/v1/models/{id}", "POST /api/v1/models/{id}/actions/delete"})
    @GetMapping("/{id}/references")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询阻止模型删除或字段变更的保护性引用", description = "聚合已保存任务定义、当前未退役任务血缘、数据服务定义、已发布指标和业务对象类型当前定义中的模型或字段引用。deletable 只表示没有保护性引用，不检查模型是否已停用；缺少 metric.view 或 ontology.view 时对应列表会隐藏具体资源，但隐藏引用仍会使 deletable=false。")
    public DataModelReferencesResponse references(@Parameter(description = "模型 UUID") @PathVariable UUID id) {
        return referenceQueryService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询当前保存任务定义中引用模型的任务")
    @GetMapping("/{id}/related-tasks")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view')")
    @Operation(summary = "查询当前保存任务定义中引用模型的任务", description = "分页查询当前保存任务定义中将该模型作为输入或输出引用的任务，可按角色筛选。")
    public PageResponse<ModelRelatedTaskResponse> searchRelatedTasks(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "按模型在任务中的 INPUT 或 OUTPUT 角色筛选") @RequestParam(required = false) ModelTaskRelationRole role,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return taskModelRelationQueryService.searchRelatedTasks(id, role, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型当前表级血缘")
    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view') and hasAuthority('service.view')")
    @Operation(summary = "查询模型当前表级血缘", description = "从当前未退役任务血缘快照查询模型的上游来源、下游消费者及标准表服务暴露关系，支持 1 或 2 层任务转换深度。图最多 200 个节点、600 条关系，超过时通过 truncated 和 warnings 说明；当前证据缺失或过期也会写入 warnings。")
    public LineageGraphResponse tableLineage(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "血缘方向：UPSTREAM 上游、DOWNSTREAM 下游或 BOTH 双向") @RequestParam(defaultValue = "BOTH") LineageDirection direction,
            @Parameter(description = "允许跨越的任务转换深度，只支持 1 或 2") @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.tableLineage(id, direction, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型当前字段级血缘")
    @GetMapping("/{id}/lineage/fields/{fieldId}")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view') and hasAuthority('service.view')")
    @Operation(summary = "查询模型当前字段级血缘", description = "从当前未退役任务血缘快照查询指定模型字段的上下游字段、任务及标准表服务暴露关系，支持 1 或 2 层任务转换深度。图最多 200 个节点、600 条关系，证据不足时通过 coverage 和 warnings 说明。")
    public LineageGraphResponse fieldLineage(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "属于当前模型的字段 UUID") @PathVariable UUID fieldId,
            @Parameter(description = "血缘方向：UPSTREAM 上游、DOWNSTREAM 下游或 BOTH 双向") @RequestParam(defaultValue = "BOTH") LineageDirection direction,
            @Parameter(description = "允许跨越的任务转换深度，只支持 1 或 2") @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.fieldLineage(id, fieldId, direction, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "批量查询模型当前字段级血缘")
    @PostMapping("/{id}/lineage/actions/query-fields")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view') and hasAuthority('service.view')")
    @Operation(summary = "批量查询模型当前字段级血缘", description = "一次选择当前模型中的最多 50 个字段，返回合并后的当前上下游血缘图及每个焦点字段的证据完整度、是否存在关系和告警。省略字段列表时选择按字段顺序排列的前 20 个，空数组返回空焦点集合；只读 POST，不修改血缘数据。")
    public LineageFieldGraphResponse queryFieldLineage(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Valid @RequestBody QueryModelFieldLineageRequest request
    ) {
        return lineageQueryService.fieldLineages(
                id, request.fieldIds(),
                request.direction() == null ? LineageDirection.BOTH : request.direction(),
                request.depth() == null ? 2 : request.depth()
        );
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览已有物理表导入后的平台字段类型",
            prerequisites = "storageDataSourceId 必须指向可连接的 JDBC 数据源；physicalTableName 是数据源默认 Catalog/Schema 下的小写普通表名。",
            relatedOperations = {"POST /api/v1/models", "GET /api/v1/models/platform-types"})
    @GetMapping("/external-table-import-preview")
    @PreAuthorize("hasAnyAuthority('model.create', 'model.update')")
    @Operation(summary = "预览已有物理表导入后的平台字段类型", description = "实时读取指定 JDBC 数据源默认 Catalog/Schema 下的已有物理表，将原生列、主键、TDengine 列角色和注释映射为平台字段，并逐列标记映射质量与能否导入。LOSSY 或 UNSUPPORTED 列会使 importable=false；不创建模型、不修改物理表。")
    public ExternalTableImportPreviewResponse previewExternalTableImport(
            @Parameter(description = "要绑定已有表的 JDBC 数据源 UUID") @RequestParam UUID storageDataSourceId,
            @Parameter(description = "数据源默认命名空间中的物理表名") @RequestParam String physicalTableName
    ) {
        return service.previewExternalTableImport(storageDataSourceId, physicalTableName);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览从 JDBC 表结构创建受管模型草稿的字段",
            prerequisites = "源必须是已启用 JDBC 数据源中的普通 TABLE；目标必须是已启用、具有 STORAGE 用途且支持受管建表的 JDBC 数据源，TDengine 不能作为目标。",
            relatedOperations = {"POST /api/v1/models/managed-drafts", "GET /api/v1/models/platform-types"})
    @PostMapping("/managed-import-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览从 JDBC 表结构创建受管模型草稿的字段", description = "读取源 JDBC 普通表元数据，经源方言映射为平台类型，再验证目标 STORAGE 方言能否表达；含 Geometry 且字段映射完整时还调用目标方言规划建表以验证空间运行能力。生成字段、模型编码和表名建议，但不检查建议编码是否占用或目标表是否存在。源视图等非 TABLE 对象不可导入；不读取业务行、不创建模型，也不执行 DDL。")
    public ManagedImportPreviewResponse previewManagedImport(
            @Valid @RequestBody ManagedImportPreviewRequest request
    ) {
        return service.previewManagedImport(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览从文件数据集逻辑表创建受管模型草稿的字段",
            prerequisites = "逻辑表必须属于指定文件数据集、处于 READY 或 SCHEMA_READY 且已有字段；目标必须是已启用、支持受管建表的 STORAGE JDBC 数据源，TDengine 不能作为目标。",
            relatedOperations = {"POST /api/v1/models/managed-drafts"})
    @PostMapping("/file-dataset-import-preview")
    @PreAuthorize("hasAuthority('model.create') and hasAuthority('filedataset.view')")
    @Operation(summary = "预览从文件数据集逻辑表创建受管模型草稿的字段", description = "读取解析阶段已保存的文件逻辑表 Schema，验证目标方言类型映射并生成受管模型字段候选。不会重新读取文件正文，也不检查建议模型编码或目标物理表是否占用；不创建模型、不建表。")
    public FileDatasetImportPreviewResponse previewFileDatasetImport(
            @Valid @RequestBody FileDatasetImportPreviewRequest request
    ) {
        return service.previewFileDatasetImport(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "根据已校对的字段原子创建受管模型草稿",
            prerequisites = "目标必须是已启用、支持受管建表的 STORAGE JDBC 数据源；模型编码和物理位置未被占用，目标物理表不存在，目录和分层可用，字段及码表能通过当前目标方言校验。",
            relatedOperations = {"POST /api/v1/models/managed-import-preview", "POST /api/v1/models/file-dataset-import-preview", "GET /api/v1/models/platform-types"})
    @PostMapping("/managed-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "根据已校对的字段原子创建受管模型草稿", description = "重新校验目录、分层、目标 STORAGE 方言、模型编码、物理位置、码表和完整字段，在管理事务外实时确认目标物理表不存在，再在一个管理库事务中创建 MANAGED 草稿及字段。预览来源不会与新模型建立关联，也不会创建物理表、复制源数据或发布模型。")
    public DataModelDetailResponse createManagedDraft(
            @Valid @RequestBody CreateManagedDraftRequest request
    ) {
        return service.createManagedDraft(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载模型元数据 Excel 导入模板")
    @GetMapping("/metadata-import-template")
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "下载模型元数据 Excel 导入模板", description = "下载模型元数据批量导入 Excel 模板；文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> metadataImportTemplate() {
        return excelFile(metadataExcelService.template());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "将选中的受管模型导出为 Excel 元数据")
    @PostMapping("/actions/export-metadata")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "将选中的受管模型导出为 Excel 元数据", description = "按请求顺序将最多 200 个 MANAGED 模型、目录路径、分层和字段元数据导出为当前 V5 Excel；任一模型不存在、重复、为 EXTERNAL 或目录路径无法解析时整次拒绝。不会读取物理表业务数据；导出文件不保证在配置变化或另一目标方言下仍可直接导入。文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> exportMetadata(
            @Valid @RequestBody ExportModelMetadataRequest request
    ) {
        return excelFile(metadataExcelService.export(request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "解析并校验模型元数据 Excel，不保存模型")
    @PostMapping(path = "/actions/preview-metadata-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "解析并校验模型元数据 Excel，不保存模型", description = "解析不超过 10 MiB 的系统 .xlsx 模板 V1–V5，并针对当前目录、启用分层、启用且类型兼容码表和目标方言校验。还会连接目标数据源逐模型确认目标表不存在，含 Geometry 时规划建表以验证空间能力；不读取业务行、不保存模型、不执行 DDL。")
    public ModelMetadataImportPreviewResponse previewMetadataImport(
            @Parameter(description = "具有 STORAGE 用途的目标 JDBC 数据源 UUID") @RequestParam UUID targetStorageDataSourceId,
            @Parameter(description = "系统模型元数据模板生成的 .xlsx 文件，最大 10 MiB") @RequestPart("file") MultipartFile file
    ) {
        return metadataExcelService.preview(targetStorageDataSourceId, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "将已校对的 Excel 模型元数据整批原子创建为受管草稿")
    @PostMapping("/actions/import-metadata")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "将已校对的 Excel 模型元数据整批原子创建为受管草稿", description = "接收校对后的 JSON 内容，不读取或绑定原 Excel，也不复用服务端预览状态。服务端按当前配置重校验最多 200 个模型，先在事务外逐个确认目标表不存在，再在一个管理库事务中创建全部 MANAGED + DRAFT 模型和字段；任一失败不保存部分模型。不会建表、发布或导入业务数据。")
    public ModelMetadataImportResultResponse importMetadata(
            @Valid @RequestBody ImportModelMetadataRequest request
    ) {
        return service.importModelMetadata(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询指定数据存储支持的平台字段类型",
            prerequisites = "storageDataSourceId 必须是具有 STORAGE 用途的 JDBC 数据源；本接口读取方言能力，不要求数据源当前启用或建立数据库连接。",
            relatedOperations = {"POST /api/v1/models"})
    @GetMapping("/platform-types")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询指定数据存储支持的平台字段类型", description = "按目标存储数据源的方言返回可用平台类型、参数范围及映射能力，用于模型字段定义。")
    public List<PlatformTypeCapabilityResponse> platformTypes(
            @Parameter(description = "目标 STORAGE JDBC 数据源 UUID") @RequestParam UUID storageDataSourceId
    ) {
        return service.platformTypeCapabilities(storageDataSourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "实时检查模型物理表")
    @GetMapping("/{id}/physical-table")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "实时检查模型物理表", description = "实时读取模型绑定物理表的存在性和结构并与模型字段比较；只读访问外部数据库。")
    public PhysicalTableInspectionResponse inspectPhysicalTable(
            @Parameter(description = "模型 UUID") @PathVariable UUID id
    ) {
        return service.inspectPhysicalTable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "刷新模型物理表的快速统计快照",
            prerequisites = "模型必须存在；关联数据源可用时执行最长 10 秒的方言统计查询。业务采集失败通常仍返回 HTTP 200，并在 lastRefreshStatus 和 message 中表达。",
            relatedOperations = {"GET /api/v1/models/{id}", "GET /api/v1/models/{id}/spatial-preview"})
    @PostMapping("/{id}/actions/refresh-physical-statistics")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "刷新模型物理表的快速统计快照", description = "对模型物理表执行最长 10 秒的方言统计查询并保存行数、占用空间及质量。采集失败、数据源缺失或停用通常仍以 HTTP 200 返回 FAILED/UNSUPPORTED 等快照；失败保留上次成功数值，物理表不存在时清空旧值。不修改物理表数据。")
    public DataModelPhysicalStatisticsResponse refreshPhysicalStatistics(
            @Parameter(description = "模型 UUID") @PathVariable UUID id
    ) {
        return physicalStatisticsService.refresh(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览模型建表 SQL",
            prerequisites = "模型必须有字段；只有 MANAGED 模型可能 supported=true，返回 SQL 仅供审阅，不能修改后提交执行。",
            relatedOperations = {"GET /api/v1/models/{id}/physical-table", "POST /api/v1/models/{id}/actions/create-physical-table"})
    @GetMapping("/{id}/physical-table/ddl")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "预览模型建表 SQL", description = "根据当前受管模型字段和目标方言生成建表 SQL 预览及诊断，不执行 DDL。")
    public PhysicalTableDdlPlanResponse physicalTableDdl(
            @Parameter(description = "模型 UUID") @PathVariable UUID id
    ) {
        return service.physicalTableDdl(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询物理表变更计划历史")
    @GetMapping("/{id}/physical-table-change-plans")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询物理表变更计划历史", description = "分页查询该模型曾生成的物理结构变更计划及执行状态，不读取外部数据库。")
    public PageResponse<DataModelPhysicalChangeResponse> searchPhysicalTableChangePlans(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchPhysicalTableChangePlans(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询物理表变更计划详情")
    @GetMapping("/{id}/physical-table-change-plans/{planId}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询物理表变更计划详情", description = "读取一个物理表变更计划冻结的目标字段、风险、前置检查、SQL 和执行结果。")
    public DataModelPhysicalChangeResponse getPhysicalTableChangePlan(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "物理表变更计划 UUID") @PathVariable UUID planId
    ) {
        return service.getPhysicalTableChangePlan(id, planId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "快速预览模型物理表数据（固定读取最多 50 条）",
            prerequisites = "模型关联数据源必须启用，真实物理表结构必须与当前模型严格匹配，并且至少存在一个非 BINARY、非 GEOMETRY 可查询字段。",
            relatedOperations = {"GET /api/v1/models/{id}/physical-table", "POST /api/v1/models/{id}/actions/query-data"})
    @GetMapping("/{id}/data-preview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "快速预览模型物理表数据（固定读取最多 50 条）", description = "物理表与模型严格匹配时，只读返回全部可查询字段的最多 50 行并多取一行判断 truncated。可用时按 ClickHouse ORDER BY 和模型主键排序；没有可用排序时顺序不稳定。DECIMAL 与日期时间转为字符串，BINARY 和 GEOMETRY 不返回。")
    public DataModelPreviewResponse previewPhysicalTable(
            @Parameter(description = "模型 UUID") @PathVariable UUID id
    ) {
        return service.previewPhysicalTable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询模型动态空间预览能力")
    @GetMapping("/{id}/spatial-preview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型动态空间预览能力", description = "检查模型 Geometry 字段、CRS、边界和动态渲染限制，返回可否预览及原因，不生成图片。")
    public DataModelSpatialPreviewResponse spatialPreview(
            @Parameter(description = "模型 UUID") @PathVariable UUID id
    ) {
        return spatialPreviewService.inspect(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "按 EPSG:3857 视口动态渲染模型空间预览 PNG")
    @GetMapping(value = "/{id}/spatial-preview/map", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "按 EPSG:3857 视口动态渲染模型空间预览 PNG", description = "按 EPSG:3857 视口读取模型空间数据并动态渲染 PNG，宽 256–1600、高 256–1200；图片响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> spatialPreviewMap(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "要渲染的模型 Geometry 字段编码") @RequestParam String geometryField,
            @Parameter(description = "EPSG:3857 视口范围，格式 west,south,east,north") @RequestParam String bbox,
            @Parameter(description = "输出 PNG 宽度，单位像素；范围以空间预览能力响应为准") @RequestParam int width,
            @Parameter(description = "输出 PNG 高度，单位像素；范围以空间预览能力响应为准") @RequestParam int height
    ) {
        SpatialPreviewImage image = spatialPreviewService.render(id, geometryField, bbox, width, height);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .header("X-Spatial-Feature-Count", Integer.toString(image.featureCount()))
                .header("X-Spatial-Skipped-Count", Integer.toString(image.skippedCount()))
                .header("X-Spatial-Truncated", Boolean.toString(image.truncated()))
                .header(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Spatial-Feature-Count, X-Spatial-Skipped-Count, X-Spatial-Truncated"
                )
                .body(image.png());
    }

    /**
     * Uses POST only because the SQL-free query contract is a structured request body; this endpoint is read-only.
     */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "按条件查询模型物理表数据",
            prerequisites = "模型关联数据源必须启用，真实物理表结构必须与当前模型严格匹配；分页偏移量最多 10,000，单次查询最长 15 秒。",
            relatedOperations = {"GET /api/v1/models/{id}", "GET /api/v1/models/{id}/physical-table"})
    @PostMapping("/{id}/actions/query-data")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "按条件查询模型物理表数据", description = "通过模型字段白名单构造只读参数化查询，不接受任意 SQL；支持 1–100 条分页、最多 20 个顶层条件和 3 个排序，偏移量最多 10,000，数据库查询最长 15 秒。可选 COUNT 单独统计总数；BINARY 和 GEOMETRY 不能返回、筛选或排序。")
    public DataModelDataQueryResponse queryPhysicalTableData(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Valid @RequestBody DataModelDataQueryRequest request
    ) {
        return service.queryPhysicalTableData(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增模型", prerequisites = "先确认未占用的模型编码与物理位置。MANAGED 首次创建没有字段；EXTERNAL 必须能实时读取已有普通表并完整映射字段。", relatedOperations = {"GET /api/v1/data-sources", "GET /api/v1/models/platform-types", "GET /api/v1/models/external-table-import-preview"})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "新增模型", description = "创建 DRAFT 模型。MANAGED 只保存元数据且初始字段为空，不创建物理表；EXTERNAL 实时读取并绑定已有普通表、自动导入全部可支持字段，但不复制数据或修改表。模型编码、字段编码和保存的物理表名统一转为小写。")
    public DataModelDetailResponse create(@Valid @RequestBody CreateDataModelRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改模型",
            prerequisites = "只有 DRAFT 可改变数据源、物理位置、模式和 ClickHouse 排序键；PUBLISHED 或 DISABLED 必须原样提交这些物理配置。",
            relatedOperations = {"GET /api/v1/models/{id}"})
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "修改模型", description = "修改模型名称、目录、说明和治理属性；字段和物理表结构由专用接口维护。")
    public DataModelDetailResponse update(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataModelRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "整体保存模型字段",
            prerequisites = "模型必须是 DRAFT 或 DISABLED，不能有 PLANNED/APPLYING 物理变更；请求是完整替换。已匹配 MANAGED 表只允许元数据变化，物理结构变化须改用变更计划。",
            relatedOperations = {"GET /api/v1/models/{id}", "POST /api/v1/models/{id}/physical-table-change-plans", "GET /api/v1/models/{id}/references"})
    @PostMapping("/{id}/actions/update-fields")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "整体保存模型字段", description = "仅允许 DRAFT 或 DISABLED，且请求整体替换字段集合。遗漏原字段即删除，受已发布指标保护的字段不能删除。EXTERNAL 必须保持真实物理结构，仅可改名称、说明、顺序和码表；MANAGED 表不存在时可直接改结构，已匹配表只能直接改元数据，结构变化须走变更计划。每次成功都会递增 schemaVersion 并协调质量规则。")
    public DataModelDetailResponse updateFields(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataModelFieldsRequest request
    ) {
        return service.updateFields(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "根据目标字段生成物理表变更计划",
            prerequisites = "模型必须是 DRAFT 或 DISABLED 的 MANAGED 模型，当前字段非空，数据源已启用，真实表与当前模型严格匹配；请求是完整目标字段快照。",
            relatedOperations = {"GET /api/v1/models/{id}", "GET /api/v1/models/{id}/physical-table", "GET /api/v1/models/{id}/physical-table-change-plans"})
    @PostMapping("/{id}/physical-table-change-plans")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "根据目标字段生成物理表变更计划", description = "仅适用于 DRAFT 或 DISABLED 的 MANAGED 模型。实时确认已存在物理表与当前字段严格匹配后，冻结完整目标字段、风险、检查、前后结构指纹和方言执行方案；新计划将旧 PLANNED 计划标为 SUPERSEDED。没有物理结构变化时返回 409，应直接保存字段元数据；不会执行 DDL。")
    public DataModelPhysicalChangeResponse createPhysicalTableChangePlan(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Valid @RequestBody CreatePhysicalTableChangePlanRequest request
    ) {
        return service.createPhysicalTableChangePlan(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "取消待执行的物理表变更计划")
    @PostMapping("/{id}/physical-table-change-plans/{planId}/actions/cancel")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "取消待执行的物理表变更计划", description = "将仍为 PLANNED 的物理表变更计划标记为取消；不执行 DDL，也不回滚已执行计划。")
    public DataModelPhysicalChangeResponse cancelPhysicalTableChangePlan(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "待取消的 PLANNED 变更计划 UUID") @PathVariable UUID planId
    ) {
        return service.cancelPhysicalTableChangePlan(id, planId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "执行物理表变更计划", prerequisites = "计划必须为 PLANNED，模型为 DRAFT 或 DISABLED 的 MANAGED 模型且 schemaVersion 未变化；executionMode 必须是计划提供的方案。执行前核对 DESTRUCTIVE 风险和全部预检。", relatedOperations = {"GET /api/v1/models/{id}/physical-table-change-plans/{planId}", "GET /api/v1/models/{id}/physical-table", "GET /api/v1/models/{id}"})
    @PostMapping("/{id}/physical-table-change-plans/{planId}/actions/execute")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "执行物理表变更计划", description = "同步执行一次 PLANNED 计划。先锁定状态为 APPLYING，再在管理事务外重新校验模型版本、真实结构指纹、数据库运行条件与数据前置检查，并只执行所选方案中冻结的 SQL；成功后原子替换模型字段、递增 schemaVersion、协调质量规则并清除统计。PARTIAL 表示物理表可能已变但元数据收尾或后置校验失败，必须先人工检查，不能直接重试。")
    public DataModelPhysicalChangeResponse executePhysicalTableChangePlan(
            @Parameter(description = "模型 UUID") @PathVariable UUID id,
            @Parameter(description = "待执行的 PLANNED 变更计划 UUID") @PathVariable UUID planId,
            @Valid @RequestBody ExecutePhysicalTableChangePlanRequest request
    ) {
        return service.executePhysicalTableChangePlan(id, planId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "根据模型字段创建物理表",
            prerequisites = "模型必须是 DRAFT、MANAGED、至少有一个字段，关联 STORAGE 数据源已启用，ClickHouse 排序键有效。",
            relatedOperations = {"GET /api/v1/models/{id}/physical-table/ddl", "GET /api/v1/models/{id}/physical-table"})
    @PostMapping("/{id}/actions/create-physical-table")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "根据模型字段创建物理表", description = "仅允许有字段的 DRAFT MANAGED 模型。按当前字段和目标方言在外部数据库执行受控建表并返回实时结构检查结果；不会发布模型。已存在或创建后仍不匹配的表不会被隐式覆盖或重建。")
    public PhysicalTableInspectionResponse createPhysicalTable(
            @Parameter(description = "MANAGED 模型 UUID") @PathVariable UUID id
    ) {
        return service.createPhysicalTable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "发布草稿或已停用模型（缺失的受管物理表自动创建）",
            prerequisites = "模型必须为 DRAFT 或 DISABLED、至少有一个字段，关联数据源已启用；EXTERNAL 表必须严格匹配，MANAGED 表必须严格匹配或尚不存在且方言支持创建。",
            relatedOperations = {"GET /api/v1/models/{id}", "GET /api/v1/models/{id}/physical-table", "GET /api/v1/models/{id}/physical-table/ddl"})
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "发布草稿或已停用模型（缺失的受管物理表自动创建）", description = "校验 DRAFT 或 DISABLED 模型字段和已启用数据源后实时检查物理表。EXTERNAL 必须已有且严格匹配；MANAGED 已有表必须严格匹配，缺失时会先在外部数据库受控建表，再提交 PUBLISHED 状态。已有漂移表不会隐式覆盖或重建；建表成功后若最终状态提交发生并发冲突，物理表可能已创建而模型仍未发布。")
    public DataModelDetailResponse publish(@Parameter(description = "模型 UUID") @PathVariable UUID id) {
        return service.publish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用模型")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "停用模型", description = "停用已发布模型，保留元数据、物理表和引用关系；不会删除或修改业务数据。")
    public DataModelDetailResponse disable(@Parameter(description = "已发布模型 UUID") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除模型元数据，不操作物理表")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.delete')")
    @Operation(summary = "删除模型元数据，不操作物理表", description = "删除草稿或已停用模型的管理元数据；不删除外部物理表，存在受保护引用时拒绝删除。")
    public void delete(@Parameter(description = "草稿或已停用模型 UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    private ResponseEntity<byte[]> excelFile(ModelMetadataExcelFile file) {
        byte[] content = file.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadataExcelService.contentType()))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content);
    }
}
