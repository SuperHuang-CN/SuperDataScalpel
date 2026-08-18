package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.lineage.service.ModelLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
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
import cn.superhuang.data.scalpel.business.model.web.request.ImportModelMetadataRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ManagedImportPreviewRequest;
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

    @GetMapping
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型")
    public PageResponse<DataModelResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型详情和字段")
    public DataModelDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/references")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询阻止模型删除的任务和数据服务引用")
    public DataModelReferencesResponse references(@PathVariable UUID id) {
        return referenceQueryService.get(id);
    }

    @GetMapping("/{id}/related-tasks")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view')")
    @Operation(summary = "查询当前保存任务定义中引用模型的任务")
    public PageResponse<ModelRelatedTaskResponse> searchRelatedTasks(
            @PathVariable UUID id,
            @RequestParam(required = false) ModelTaskRelationRole role,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return taskModelRelationQueryService.searchRelatedTasks(id, role, request);
    }

    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view') and hasAuthority('service.view')")
    @Operation(summary = "查询模型当前表级血缘")
    public LineageGraphResponse tableLineage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "BOTH") LineageDirection direction,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.tableLineage(id, direction, depth);
    }

    @GetMapping("/{id}/lineage/fields/{fieldId}")
    @PreAuthorize("hasAuthority('model.view') and hasAuthority('task.view') and hasAuthority('service.view')")
    @Operation(summary = "查询模型当前字段级血缘")
    public LineageGraphResponse fieldLineage(
            @PathVariable UUID id,
            @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "BOTH") LineageDirection direction,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.fieldLineage(id, fieldId, direction, depth);
    }

    @GetMapping("/external-table-import-preview")
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "预览已有物理表导入后的平台字段类型")
    public ExternalTableImportPreviewResponse previewExternalTableImport(
            @RequestParam UUID storageDataSourceId,
            @RequestParam String physicalTableName
    ) {
        return service.previewExternalTableImport(storageDataSourceId, physicalTableName);
    }

    @PostMapping("/managed-import-preview")
    @PreAuthorize("hasAuthority('datasource.metadata')")
    @Operation(summary = "预览从 JDBC 表结构创建受管模型草稿的字段")
    public ManagedImportPreviewResponse previewManagedImport(
            @Valid @RequestBody ManagedImportPreviewRequest request
    ) {
        return service.previewManagedImport(request);
    }

    @PostMapping("/managed-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "根据已校对的字段原子创建受管模型草稿")
    public DataModelDetailResponse createManagedDraft(
            @Valid @RequestBody CreateManagedDraftRequest request
    ) {
        return service.createManagedDraft(request);
    }

    @GetMapping("/metadata-import-template")
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "下载模型元数据 Excel 导入模板")
    public ResponseEntity<byte[]> metadataImportTemplate() {
        return excelFile(metadataExcelService.template());
    }

    @PostMapping("/actions/export-metadata")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "将选中的受管模型导出为 Excel 元数据")
    public ResponseEntity<byte[]> exportMetadata(
            @Valid @RequestBody ExportModelMetadataRequest request
    ) {
        return excelFile(metadataExcelService.export(request));
    }

    @PostMapping(path = "/actions/preview-metadata-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "解析并校验模型元数据 Excel，不保存模型")
    public ModelMetadataImportPreviewResponse previewMetadataImport(
            @RequestParam UUID targetStorageDataSourceId,
            @RequestPart("file") MultipartFile file
    ) {
        return metadataExcelService.preview(targetStorageDataSourceId, file);
    }

    @PostMapping("/actions/import-metadata")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "将已校对的 Excel 模型元数据整批原子创建为受管草稿")
    public ModelMetadataImportResultResponse importMetadata(
            @Valid @RequestBody ImportModelMetadataRequest request
    ) {
        return service.importModelMetadata(request);
    }

    @GetMapping("/platform-types")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询指定数据存储支持的平台字段类型")
    public List<PlatformTypeCapabilityResponse> platformTypes(@RequestParam UUID storageDataSourceId) {
        return service.platformTypeCapabilities(storageDataSourceId);
    }

    @GetMapping("/{id}/physical-table")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "实时检查模型物理表")
    public PhysicalTableInspectionResponse inspectPhysicalTable(@PathVariable UUID id) {
        return service.inspectPhysicalTable(id);
    }

    @PostMapping("/{id}/actions/refresh-physical-statistics")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "刷新模型物理表的快速统计快照")
    public DataModelPhysicalStatisticsResponse refreshPhysicalStatistics(@PathVariable UUID id) {
        return physicalStatisticsService.refresh(id);
    }

    @GetMapping("/{id}/physical-table/ddl")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "预览模型建表 SQL")
    public PhysicalTableDdlPlanResponse physicalTableDdl(@PathVariable UUID id) {
        return service.physicalTableDdl(id);
    }

    @GetMapping("/{id}/physical-table-change-plans")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询物理表变更计划历史")
    public PageResponse<DataModelPhysicalChangeResponse> searchPhysicalTableChangePlans(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchPhysicalTableChangePlans(id, request);
    }

    @GetMapping("/{id}/physical-table-change-plans/{planId}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询物理表变更计划详情")
    public DataModelPhysicalChangeResponse getPhysicalTableChangePlan(
            @PathVariable UUID id,
            @PathVariable UUID planId
    ) {
        return service.getPhysicalTableChangePlan(id, planId);
    }

    @GetMapping("/{id}/data-preview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "快速预览模型物理表数据（固定读取最多 50 条）")
    public DataModelPreviewResponse previewPhysicalTable(@PathVariable UUID id) {
        return service.previewPhysicalTable(id);
    }

    @GetMapping("/{id}/spatial-preview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型动态空间预览能力")
    public DataModelSpatialPreviewResponse spatialPreview(@PathVariable UUID id) {
        return spatialPreviewService.inspect(id);
    }

    @GetMapping(value = "/{id}/spatial-preview/map", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "按 EPSG:3857 视口动态渲染模型空间预览 PNG")
    public ResponseEntity<byte[]> spatialPreviewMap(
            @PathVariable UUID id,
            @RequestParam String geometryField,
            @RequestParam String bbox,
            @RequestParam int width,
            @RequestParam int height
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
    @PostMapping("/{id}/actions/query-data")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "按条件查询模型物理表数据")
    public DataModelDataQueryResponse queryPhysicalTableData(
            @PathVariable UUID id,
            @Valid @RequestBody DataModelDataQueryRequest request
    ) {
        return service.queryPhysicalTableData(id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "新增模型")
    public DataModelDetailResponse create(@Valid @RequestBody CreateDataModelRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "修改模型")
    public DataModelDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataModelRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/update-fields")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "整体保存模型字段")
    public DataModelDetailResponse updateFields(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataModelFieldsRequest request
    ) {
        return service.updateFields(id, request);
    }

    @PostMapping("/{id}/physical-table-change-plans")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "根据目标字段生成物理表变更计划")
    public DataModelPhysicalChangeResponse createPhysicalTableChangePlan(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePhysicalTableChangePlanRequest request
    ) {
        return service.createPhysicalTableChangePlan(id, request);
    }

    @PostMapping("/{id}/physical-table-change-plans/{planId}/actions/cancel")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "取消待执行的物理表变更计划")
    public DataModelPhysicalChangeResponse cancelPhysicalTableChangePlan(
            @PathVariable UUID id,
            @PathVariable UUID planId
    ) {
        return service.cancelPhysicalTableChangePlan(id, planId);
    }

    @PostMapping("/{id}/physical-table-change-plans/{planId}/actions/execute")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "执行物理表变更计划")
    public DataModelPhysicalChangeResponse executePhysicalTableChangePlan(
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody ExecutePhysicalTableChangePlanRequest request
    ) {
        return service.executePhysicalTableChangePlan(id, planId, request);
    }

    @PostMapping("/{id}/actions/create-physical-table")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "根据模型字段创建物理表")
    public PhysicalTableInspectionResponse createPhysicalTable(@PathVariable UUID id) {
        return service.createPhysicalTable(id);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "发布模型（物理表必须与字段定义一致）")
    public DataModelDetailResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "停用模型")
    public DataModelDetailResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "重新启用模型")
    public DataModelDetailResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.delete')")
    @Operation(summary = "删除模型元数据，不操作物理表")
    public void delete(@PathVariable UUID id) {
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
