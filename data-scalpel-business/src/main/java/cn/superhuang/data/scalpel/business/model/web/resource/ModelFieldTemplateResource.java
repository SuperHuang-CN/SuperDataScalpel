package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.model.service.ModelFieldTemplateService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelFieldTemplateResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/model-field-templates")
@Tag(name = "常用字段模板")
public class ModelFieldTemplateResource {

    private final ModelFieldTemplateService service;

    public ModelFieldTemplateResource(ModelFieldTemplateService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "分页查询常用字段模板及字段快照",
            relatedOperations = {"POST /api/v1/models/managed-drafts", "POST /api/v1/models/{id}/actions/update-fields"})
    @GetMapping
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "分页查询常用字段模板及字段快照", description = "使用通用 Search DSL 分页查询启用和停用模板，每条结果都包含完整字段快照；省略排序时按 category、sortOrder、name、code。当前没有服务端应用模板命令，调用方复制字段到模型请求后不再与模板关联。")
    public PageResponse<ModelFieldTemplateResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询常用字段模板详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询常用字段模板详情", description = "读取常用字段模板的元数据、状态和完整字段定义。")
    public ModelFieldTemplateResponse get(@Parameter(description = "常用字段模板 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增常用字段模板")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "新增常用字段模板", description = "原子创建初始启用、版本为 1 的模板及 1 到 100 个字段。模板编码转为大写、字段编码转为小写；字段 id 必须为空，新码表绑定会校验启用状态和值可表达性。")
    public ModelFieldTemplateResponse create(
            @Valid @RequestBody CreateModelFieldTemplateRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "原子修改常用字段模板及其字段")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "原子修改常用字段模板及其字段", description = "锁定模板并校验 expectedVersion 后，原子修改元数据、整体替换 1 到 100 个字段并递增版本；遗漏原字段即删除，非空字段 id 保留身份。不会同步修改此前已复制到模型中的字段。")
    public ModelFieldTemplateResponse update(
            @Parameter(description = "常用字段模板 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateModelFieldTemplateRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用常用字段模板")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "启用常用字段模板", description = "幂等启用模板；状态实际变化时版本递增，已启用时原样返回且版本不变。查询仍会返回停用模板，enabled 供调用方决定是否允许选择。")
    public ModelFieldTemplateResponse enable(@Parameter(description = "常用字段模板 UUID") @PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用常用字段模板")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "停用常用字段模板", description = "幂等停用模板；状态实际变化时版本递增，已停用时原样返回且版本不变。它不删除模板，也不影响已经复制到模型的字段。")
    public ModelFieldTemplateResponse disable(@Parameter(description = "常用字段模板 UUID") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除常用字段模板；不影响已经复制到模型的字段")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "删除常用字段模板；不影响已经复制到模型的字段", description = "删除模板及其模板字段；已经复制到模型的字段是独立快照，不受影响。")
    public void delete(@Parameter(description = "常用字段模板 UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
