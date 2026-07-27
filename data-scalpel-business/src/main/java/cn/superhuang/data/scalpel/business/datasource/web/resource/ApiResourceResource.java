package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceTestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-sources/{dataSourceId}/api-resources")
@Tag(name = "HTTP API 资源")
public class ApiResourceResource {

    private final ApiResourceService service;
    private final ApiResourceRuntimeService runtimeService;

    public ApiResourceResource(ApiResourceService service, ApiResourceRuntimeService runtimeService) {
        this.service = service;
        this.runtimeService = runtimeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 HTTP API 资源")
    public List<ApiResourceResponse> list(@PathVariable UUID dataSourceId) {
        return service.list(dataSourceId);
    }

    @GetMapping("/{resourceId}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 HTTP API 资源详情")
    public ApiResourceResponse get(@PathVariable UUID dataSourceId, @PathVariable UUID resourceId) {
        return service.get(dataSourceId, resourceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "新增 HTTP API 资源")
    public ApiResourceResponse create(
            @PathVariable UUID dataSourceId,
            @Valid @RequestBody CreateApiResourceRequest request
    ) {
        return service.create(dataSourceId, request);
    }

    @PostMapping("/{resourceId}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改 HTTP API 资源")
    public ApiResourceResponse update(
            @PathVariable UUID dataSourceId,
            @PathVariable UUID resourceId,
            @Valid @RequestBody UpdateApiResourceRequest request
    ) {
        return service.update(dataSourceId, resourceId, request);
    }

    @PostMapping("/{resourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除 HTTP API 资源")
    public void delete(@PathVariable UUID dataSourceId, @PathVariable UUID resourceId) {
        service.delete(dataSourceId, resourceId);
    }

    @PostMapping("/{resourceId}/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试 HTTP API 资源")
    public ApiResourceTestResponse test(
            @PathVariable UUID dataSourceId,
            @PathVariable UUID resourceId,
            @Valid @RequestBody(required = false) TestApiResourceRequest request
    ) {
        return runtimeService.test(dataSourceId, resourceId, request);
    }
}
