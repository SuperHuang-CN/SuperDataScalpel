package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.lineage.service.DataServiceLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageFieldGraphResponse;
import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.DataServiceRelatedModelService;
import cn.superhuang.data.scalpel.business.service.StandardDataServiceModelCandidateService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServiceModelCandidateService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServicePreviewService;
import cn.superhuang.data.scalpel.business.service.SpatialDataServiceStyleService;
import cn.superhuang.data.scalpel.business.service.SpatialStyleFieldProfileService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateSpatialStyleRequest;
import cn.superhuang.data.scalpel.business.service.web.request.QuerySpatialStyleSldRequest;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleSldResponse;
import cn.superhuang.data.scalpel.business.service.web.request.SpatialStyleFieldProfileRequest;
import cn.superhuang.data.scalpel.business.service.web.request.RenderSpatialStylePreviewRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SqlServiceTestRequest;
import cn.superhuang.data.scalpel.business.service.web.request.QueryDataServiceFieldLineageRequest;
import cn.superhuang.data.scalpel.business.service.web.request.PublishDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceDetailResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceSummaryResponse;
import cn.superhuang.data.scalpel.business.service.web.response.GatewayDataServicePublicationResponse;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestResponse;
import cn.superhuang.data.scalpel.business.service.web.response.StandardDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceModelCandidateResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServicePreviewResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceStyleResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleFieldProfileResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "数据服务")
@RestController
@RequestMapping("/api/v1/data-services")
public class DataServiceResource {

    private final DataServiceManagementService service;
    private final StandardDataServiceModelCandidateService modelCandidateService;
    private final DataServiceRelatedModelService relatedModelService;
    private final SpatialDataServiceModelCandidateService spatialModelCandidateService;
    private final SpatialDataServicePreviewService spatialPreviewService;
    private final SpatialDataServiceStyleService spatialStyleService;
    private final SpatialStyleFieldProfileService spatialStyleFieldProfileService;
    private final DataServiceLineageQueryService lineageQueryService;

    public DataServiceResource(
            DataServiceManagementService service,
            StandardDataServiceModelCandidateService modelCandidateService,
            SpatialDataServiceModelCandidateService spatialModelCandidateService,
            SpatialDataServicePreviewService spatialPreviewService,
            SpatialDataServiceStyleService spatialStyleService,
            SpatialStyleFieldProfileService spatialStyleFieldProfileService,
            DataServiceRelatedModelService relatedModelService,
            DataServiceLineageQueryService lineageQueryService
    ) {
        this.service = service;
        this.modelCandidateService = modelCandidateService;
        this.spatialModelCandidateService = spatialModelCandidateService;
        this.spatialPreviewService = spatialPreviewService;
        this.spatialStyleService = spatialStyleService;
        this.spatialStyleFieldProfileService = spatialStyleFieldProfileService;
        this.relatedModelService = relatedModelService;
        this.lineageQueryService = lineageQueryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间模型候选")
    @GetMapping("/{id}/spatial-model-candidates")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public PageResponse<SpatialDataServiceModelCandidateResponse> spatialModelCandidates(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request,
            @RequestParam(defaultValue = "false") boolean includeUnavailable
    ) {
        return spatialModelCandidateService.search(id, request, includeUnavailable);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询列表")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    public PageResponse<DataServiceSummaryResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查看详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    public DataServiceDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间预览能力")
    @GetMapping("/{id}/spatial-preview")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialDataServicePreviewResponse spatialPreview(@PathVariable UUID id) {
        return spatialPreviewService.inspect(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染地图预览")
    @GetMapping(value = "/{id}/spatial-preview/map", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('service.view')")
    public ResponseEntity<byte[]> spatialPreviewMap(
            @PathVariable UUID id,
            @RequestParam String bbox,
            @RequestParam int width,
            @RequestParam int height
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.render(id, bbox, width, height));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：读取图例")
    @GetMapping(value = "/{id}/spatial-preview/legend", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('service.view')")
    public ResponseEntity<byte[]> spatialPreviewLegend(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderLegend(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询空间样式")
    @GetMapping("/{id}/spatial-style")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialDataServiceStyleResponse spatialStyle(@PathVariable UUID id) {
        return spatialStyleService.get(id);
    }

    /** Read-only query: compile draft SLD without persistence or remote execution. */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：读取空间样式 SLD")
    @PostMapping("/{id}/actions/query-spatial-style-sld")
    @PreAuthorize("hasAuthority('service.view')")
    public SpatialStyleSldResponse querySpatialStyleSld(
            @PathVariable UUID id,
            @Valid @RequestBody QuerySpatialStyleSldRequest request
    ) {
        return spatialStyleService.querySld(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改空间样式")
    @PostMapping("/{id}/actions/update-spatial-style")
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialDataServiceStyleResponse updateSpatialStyle(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSpatialStyleRequest request
    ) {
        return spatialStyleService.updateCartography(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询样式字段分布")
    @PostMapping("/{id}/actions/query-spatial-style-field-profile")
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialStyleFieldProfileResponse querySpatialStyleFieldProfile(
            @PathVariable UUID id,
            @Valid @RequestBody SpatialStyleFieldProfileRequest request
    ) {
        return spatialStyleFieldProfileService.profile(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染样式预览")
    @PostMapping(
            value = "/{id}/actions/render-spatial-style-preview",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @PreAuthorize("hasAuthority('service.update')")
    public ResponseEntity<byte[]> renderSpatialStylePreview(
            @PathVariable UUID id,
            @Valid @RequestBody RenderSpatialStylePreviewRequest request
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderDraft(id, request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：渲染上传样式预览")
    @PostMapping(
            value = "/{id}/actions/render-spatial-style-preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_PNG_VALUE
    )
    @PreAuthorize("hasAuthority('service.update')")
    public ResponseEntity<byte[]> renderUploadedSpatialStylePreview(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String bbox,
            @RequestParam int width,
            @RequestParam int height
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(spatialPreviewService.renderUploadedDraft(id, file, bbox, width, height));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：上传 SLD 样式")
    @PostMapping(value = "/{id}/actions/upload-spatial-sld", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('service.update')")
    public SpatialDataServiceStyleResponse uploadSpatialSld(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        return spatialStyleService.upload(id, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：应用空间样式")
    @PostMapping("/{id}/actions/apply-spatial-style")
    @PreAuthorize("hasAuthority('service.publish')")
    public SpatialDataServiceStyleResponse applySpatialStyle(@PathVariable UUID id) {
        return spatialStyleService.apply(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询已发布网关服务")
    @GetMapping("/gateway-publications")
    @PreAuthorize("hasAuthority('service.view')")
    public List<GatewayDataServicePublicationResponse> publishedGatewayServices(
            @RequestParam DataServiceAccessMode accessMode
    ) {
        return service.publishedGatewayServices(accessMode);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：创建")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.create')")
    public DataServiceDetailResponse create(@Valid @RequestBody CreateDataServiceRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataServiceRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：修改定义")
    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceDetailResponse updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataServiceDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询标准模型候选")
    @GetMapping("/{id}/standard-model-candidates")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public PageResponse<StandardDataServiceModelCandidateResponse> standardModelCandidates(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request,
            @RequestParam(defaultValue = "false") boolean includeUnavailable
    ) {
        return modelCandidateService.search(id, request, includeUnavailable);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询关联模型")
    @GetMapping("/{id}/related-models")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view')")
    public List<DataServiceRelatedModelResponse> relatedModels(@PathVariable UUID id) {
        return relatedModelService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询表级血缘")
    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse tableLineage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.tableLineage(id, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：查询字段血缘")
    @GetMapping("/{id}/lineage/fields/{fieldId}")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageGraphResponse fieldLineage(
            @PathVariable UUID id,
            @PathVariable UUID fieldId,
            @RequestParam(defaultValue = "2") int depth
    ) {
        return lineageQueryService.fieldLineage(id, fieldId, depth);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务：批量查询字段血缘")
    @PostMapping("/{id}/lineage/actions/query-fields")
    @PreAuthorize("hasAuthority('service.view') and hasAuthority('model.view') and hasAuthority('task.view')")
    public LineageFieldGraphResponse queryFieldLineage(
            @PathVariable UUID id,
            @Valid @RequestBody QueryDataServiceFieldLineageRequest request
    ) {
        return lineageQueryService.fieldLineages(
                id, request.fieldIds(), request.depth() == null ? 2 : request.depth()
        );
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：测试 SQL")
    @PostMapping("/actions/test-sql")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public SqlServiceTestResponse testSql(@Valid @RequestBody SqlServiceTestRequest request) {
        return service.testSql(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：启用")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：发布")
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse publish(
            @PathVariable UUID id,
            @Valid @RequestBody PublishDataServiceRequest request
    ) {
        return service.publish(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：对账网关状态")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse reconcileGateway(@PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：取消发布")
    @PostMapping("/{id}/actions/unpublish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse unpublish(@PathVariable UUID id) {
        return service.unpublish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：停用")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务：清理部署")
    @PostMapping("/{id}/actions/cleanup-deployment")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceDetailResponse cleanupDeployment(@PathVariable UUID id) {
        return service.cleanupDeployment(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "数据服务：删除")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
