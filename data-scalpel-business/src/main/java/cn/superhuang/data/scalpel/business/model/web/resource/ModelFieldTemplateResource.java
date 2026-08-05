package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.model.service.ModelFieldTemplateService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelFieldTemplateRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelFieldTemplateResponse;
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

    @GetMapping
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "分页查询常用字段模板及字段快照")
    public PageResponse<ModelFieldTemplateResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询常用字段模板详情")
    public ModelFieldTemplateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "新增常用字段模板")
    public ModelFieldTemplateResponse create(
            @Valid @RequestBody CreateModelFieldTemplateRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "原子修改常用字段模板及其字段")
    public ModelFieldTemplateResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModelFieldTemplateRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "启用常用字段模板")
    public ModelFieldTemplateResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "停用常用字段模板")
    public ModelFieldTemplateResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "删除常用字段模板；不影响已经复制到模型的字段")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
