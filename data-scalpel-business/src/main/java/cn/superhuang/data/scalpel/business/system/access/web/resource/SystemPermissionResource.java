package cn.superhuang.data.scalpel.business.system.access.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemPermissionResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/permissions")
@Tag(name = "系统管理-权限管理")
public class SystemPermissionResource {

    private final SystemAccessService service;

    public SystemPermissionResource(SystemAccessService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询系统权限")
    @GetMapping
    @PreAuthorize("hasAuthority('system.permission.view')")
    @Operation(summary = "查询系统权限")
    public PageResponse<SystemPermissionResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchPermissions(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询系统权限详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system.permission.view')")
    @Operation(summary = "查询系统权限详情")
    public SystemPermissionResponse get(@PathVariable UUID id) {
        return service.getPermission(id);
    }
}
