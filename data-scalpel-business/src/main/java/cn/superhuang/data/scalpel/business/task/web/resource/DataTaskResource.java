package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateLocalSqlTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataTaskResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlDefinitionValidationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlTaskDefinitionResponse;
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
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务管理")
public class DataTaskResource {

    private final DataTaskService service;

    public DataTaskResource(DataTaskService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务")
    public PageResponse<DataTaskResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务摘要")
    public DataTaskResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询本地 SQL 任务定义")
    public LocalSqlTaskDefinitionResponse getDefinition(@PathVariable UUID id) {
        return service.getDefinition(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.create')")
    @Operation(summary = "创建本地 SQL 任务")
    public DataTaskResponse create(@Valid @RequestBody CreateDataTaskRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改任务基本信息")
    public DataTaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataTaskRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存本地 SQL 任务定义")
    public LocalSqlTaskDefinitionResponse updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocalSqlTaskDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @PostMapping("/{id}/actions/validate-definition")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "校验本地 SQL 任务定义")
    public LocalSqlDefinitionValidationResponse validateDefinition(@PathVariable UUID id) {
        return service.validateDefinition(id);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "发布本地 SQL 任务")
    public DataTaskResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "停用本地 SQL 任务")
    public DataTaskResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "重新启用本地 SQL 任务")
    public DataTaskResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.delete')")
    @Operation(summary = "删除未发布本地 SQL 任务")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
