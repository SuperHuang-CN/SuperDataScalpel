package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreatePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ExecutePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalChangeResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableDdlPlanResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableInspectionResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PlatformTypeCapabilityResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "模型管理")
public class DataModelResource {

    private final DataModelService service;

    public DataModelResource(DataModelService service) {
        this.service = service;
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

    @GetMapping("/external-table-import-preview")
    @PreAuthorize("hasAuthority('model.create')")
    @Operation(summary = "预览已有物理表导入后的平台字段类型")
    public ExternalTableImportPreviewResponse previewExternalTableImport(
            @RequestParam UUID storageDataSourceId,
            @RequestParam String physicalTableName
    ) {
        return service.previewExternalTableImport(storageDataSourceId, physicalTableName);
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
}
