package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.model.service.ModelWarehouseLayerService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerResponse;
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
@RequestMapping("/api/v1/model-warehouse-layers")
@Tag(name = "数仓分层配置")
public class ModelWarehouseLayerResource {

    private static final String READ_AUTHORITY = """
            hasAnyAuthority(
                'system.configuration.view',
                'model.view',
                'model.create',
                'model.update'
            )
            """;

    private final ModelWarehouseLayerService service;

    public ModelWarehouseLayerResource(ModelWarehouseLayerService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询动态数仓分层")
    @GetMapping
    @PreAuthorize(READ_AUTHORITY)
    @Operation(summary = "查询动态数仓分层")
    public PageResponse<ModelWarehouseLayerResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增数仓分层")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "新增数仓分层")
    public ModelWarehouseLayerResponse create(
            @Valid @RequestBody CreateModelWarehouseLayerRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改数仓分层")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "修改数仓分层")
    public ModelWarehouseLayerResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModelWarehouseLayerRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用数仓分层")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "启用数仓分层")
    public ModelWarehouseLayerResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用数仓分层")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "停用数仓分层")
    public ModelWarehouseLayerResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除未被模型或其他分层规范引用的数仓分层")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "删除未被模型或其他分层规范引用的数仓分层")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
