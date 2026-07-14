package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
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

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('model.publish')")
    @Operation(summary = "发布模型（第一版仅更新元数据状态）")
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
