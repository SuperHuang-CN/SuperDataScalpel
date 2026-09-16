package cn.superhuang.data.scalpel.business.ontology.web.resource;

import cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeService;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectPreviewCandidatesRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectPreviewRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.BusinessObjectRelatedPreviewRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.CreateBusinessObjectTypeRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.UpdateBusinessObjectTypeDefinitionRequest;
import cn.superhuang.data.scalpel.business.ontology.web.request.UpdateBusinessObjectTypeRequest;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPreviewCandidatesResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectPreviewResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectRelatedPreviewResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectRelationResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeGraphResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeResponse;
import cn.superhuang.data.scalpel.business.ontology.web.response.BusinessObjectTypeValidationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Administrative API only. It deliberately does not expose business capabilities as executable tools. */
@RestController
@RequestMapping("/api/v1/business-object-types")
@Tag(name = "业务建模", description = "维护业务对象类型当前定义、来源、属性、关系和仅登记的业务能力。保存后直接生效，不维护草稿、发布版本或业务数据副本。")
public class BusinessObjectTypeResource {

    private final BusinessObjectTypeService service;

    public BusinessObjectTypeResource(BusinessObjectTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ontology.view')")
    @Operation(summary = "分页查询业务对象类型", description = "按通用 Search DSL 查询当前业务对象类型，返回一份直接生效的定义摘要和当前诊断；不会访问来源业务数据。")
    public PageResponse<BusinessObjectTypeResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/graph")
    @PreAuthorize("hasAuthority('ontology.view')")
    @Operation(
            summary = "读取本体总览拓扑",
            description = "返回全部业务对象类型最近保存配置的轻量节点和规范方向关系。一份关系只返回一次；不读取来源业务记录、不执行全类型校验或连接检查，也不保存画布布局。没有 model.view 权限时仍返回拓扑，但省略来源模型和关联字段的名称、编码。"
    )
    public BusinessObjectTypeGraphResponse graph() {
        return service.graph();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ontology.manage')")
    @Operation(summary = "创建业务对象类型", description = "创建默认启用的对象类型和空定义，返回 201。可在后续分步保存来源、属性、关系和能力；不创建业务对象数据。")
    public BusinessObjectTypeResponse create(@Valid @RequestBody CreateBusinessObjectTypeRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ontology.view')")
    @Operation(summary = "读取业务对象类型详情", description = "返回对象类型基础资料、唯一当前定义、关系入口和静态配置诊断；不会读取来源业务数据。")
    public BusinessObjectTypeResponse get(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('ontology.manage')")
    @Operation(summary = "更新业务对象类型基础资料", description = "更新名称、目录、负责人和业务说明；编码不可修改，也不会修改当前建模定义。")
    public BusinessObjectTypeResponse update(
            @Parameter(description = "业务对象类型 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessObjectTypeRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('ontology.manage') and hasAuthority('model.view')")
    @Operation(summary = "保存当前业务建模定义", description = "以请求中的完整定义覆盖当前来源、属性分组、关系和能力配置，并在同一管理库事务更新引用保护投影。保存后下次预览立即使用新配置；允许未完成配置，但无效引用不能保存。")
    public BusinessObjectTypeResponse updateDefinition(
            @Parameter(description = "业务对象类型 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateBusinessObjectTypeDefinitionRequest request
    ) {
        return service.saveDefinition(id, request);
    }

    @GetMapping("/{id}/validation")
    @PreAuthorize("hasAuthority('ontology.view') and hasAuthority('model.view')")
    @Operation(summary = "校验当前业务建模定义", description = "检查来源模型生命周期、字段、类型、映射、关系目标和业务编码，不扫描全量业务数据，也不会修改配置。")
    public BusinessObjectTypeValidationResponse validation(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.validation(id);
    }

    @GetMapping("/{id}/health")
    @PreAuthorize("hasAuthority('ontology.view') and hasAuthority('model.view')")
    @Operation(summary = "读取业务对象类型静态配置诊断", description = "返回与当前定义校验一致的结构和引用诊断。该接口不连接来源检查实时可用性，读取时间也不代表来源数据时间。")
    public BusinessObjectTypeValidationResponse health(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.health(id);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('ontology.manage')")
    @Operation(summary = "启用业务对象类型", description = "使对象类型可以被关系引用和数据预览；不会补齐未完成配置或访问业务数据。")
    public BusinessObjectTypeResponse enable(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('ontology.manage')")
    @Operation(summary = "停用业务对象类型", description = "停止该对象类型的数据预览和新增关系引用。仍有入向关系时会拒绝停用，需先保存解除关系后的定义。")
    public BusinessObjectTypeResponse disable(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ontology.manage')")
    @Operation(summary = "删除业务对象类型", description = "删除当前定义及其引用投影。仍被其他对象类型关系引用时会拒绝；不会删除或修改任何来源业务数据。")
    public void delete(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/{id}/relations")
    @PreAuthorize("hasAuthority('ontology.view')")
    @Operation(summary = "查询对象类型可访问的业务关系", description = "返回当前对象定义的出向关系和其他对象定义带来的反向关系，供类型关系图和跳转使用；不会读取具体业务对象。")
    public List<BusinessObjectRelationResponse> relations(@Parameter(description = "业务对象类型 UUID") @PathVariable UUID id) {
        return service.relations(id);
    }

    @PostMapping("/{id}/actions/query-preview-candidates")
    @PreAuthorize("hasAuthority('ontology.view') and hasAuthority('model.view')")
    @Operation(summary = "只读查询对象预览候选", description = "按当前已保存的主来源固定筛选分页读取候选对象身份和显示名称；不会创建对象数据副本或修改来源数据。")
    public BusinessObjectPreviewCandidatesResponse previewCandidates(
            @Parameter(description = "业务对象类型 UUID") @PathVariable UUID id,
            @Valid @RequestBody BusinessObjectPreviewCandidatesRequest request
    ) {
        return service.previewCandidates(id, request);
    }

    @PostMapping("/{id}/actions/query-preview")
    @PreAuthorize("hasAuthority('ontology.view') and hasAuthority('model.view')")
    @Operation(summary = "只读预览具体业务对象", description = "按对象唯一标识实时读取主来源及补充来源。服务端分源查询后组合结果，不执行跨数据库 JOIN，也不保存查询结果。")
    public BusinessObjectPreviewResponse preview(
            @Parameter(description = "业务对象类型 UUID") @PathVariable UUID id,
            @Valid @RequestBody BusinessObjectPreviewRequest request
    ) {
        return service.preview(id, request);
    }

    @PostMapping("/{id}/actions/query-preview-related")
    @PreAuthorize("hasAuthority('ontology.view') and hasAuthority('model.view')")
    @Operation(summary = "只读预览具体关联对象", description = "沿当前保存关系的字段映射分页读取关联对象身份。关系实例由来源数据实时解析，不复制或手工维护每条关系数据。")
    public BusinessObjectRelatedPreviewResponse previewRelated(
            @Parameter(description = "业务对象类型 UUID") @PathVariable UUID id,
            @Valid @RequestBody BusinessObjectRelatedPreviewRequest request
    ) {
        return service.previewRelated(id, request);
    }
}
