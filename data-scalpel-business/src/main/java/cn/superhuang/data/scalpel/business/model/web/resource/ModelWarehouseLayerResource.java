package cn.superhuang.data.scalpel.business.model.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.model.service.ModelWarehouseLayerService;
import cn.superhuang.data.scalpel.business.model.web.request.CreateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateModelWarehouseLayerRequest;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerResponse;
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
    @Operation(summary = "查询动态数仓分层", description = "使用通用 Search DSL 分页查询全部启用和停用分层，并返回建议编码前缀、允许输入关系及引用计数。输入关系当前仅供建模规划，不参与任务保存、发布或运行校验。")
    public PageResponse<ModelWarehouseLayerResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增数仓分层")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "新增数仓分层", description = "原子创建初始启用的扁平数仓分层及完整允许输入关系。分层编码转为大写并全局唯一；名称、模型编码前缀和规划规则不要求唯一。")
    public ModelWarehouseLayerResponse create(
            @Valid @RequestBody CreateModelWarehouseLayerRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改数仓分层")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "修改数仓分层", description = "原子修改分层元数据、建议前缀、输入策略并整体替换允许输入关系。已有模型引用时不能修改分层编码；其他修改不会改写模型，也不会触发任务校验。")
    public ModelWarehouseLayerResponse update(
            @Parameter(description = "数仓分层 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateModelWarehouseLayerRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用数仓分层")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "启用数仓分层", description = "幂等启用分层，使其可用于新建模型或把模型改分配到该分层；不会修改已有模型或任务。")
    public ModelWarehouseLayerResponse enable(@Parameter(description = "数仓分层 UUID") @PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用数仓分层")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "停用数仓分层", description = "幂等停用分层，阻止新模型或模型改分配到该分层；已有模型关联及其他分层已保存的允许输入关系保持不变，任务运行不受阻止。")
    public ModelWarehouseLayerResponse disable(@Parameter(description = "数仓分层 UUID") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除未被模型或其他分层规范引用的数仓分层")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "删除未被模型或其他分层规范引用的数仓分层", description = "仅当没有模型引用、也没有其他分层将其列为允许输入时删除并返回 204。该分层自身保存的允许输入关系会同时删除，自引用不单独阻止删除。")
    public void delete(@Parameter(description = "数仓分层 UUID") @PathVariable UUID id) {
        service.delete(id);
    }
}
