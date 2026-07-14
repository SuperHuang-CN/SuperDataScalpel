package cn.superhuang.data.scalpel.business.system.access.web.resource;

import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import cn.superhuang.data.scalpel.business.system.access.web.request.CreateSystemRoleRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemRolePermissionsRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemRoleRequest;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemRoleResponse;
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
@RequestMapping("/api/v1/system/roles")
@Tag(name = "系统管理-角色管理")
public class SystemRoleResource {

    private final SystemAccessService service;

    public SystemRoleResource(SystemAccessService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system.role.view')")
    @Operation(summary = "查询系统角色")
    public PageResponse<SystemRoleResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchRoles(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system.role.view')")
    @Operation(summary = "查询系统角色详情")
    public SystemRoleResponse get(@PathVariable UUID id) {
        return service.getRole(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.role.manage')")
    @Operation(summary = "新增系统角色")
    public SystemRoleResponse create(@Valid @RequestBody CreateSystemRoleRequest request) {
        return service.createRole(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.role.manage')")
    @Operation(summary = "修改系统角色")
    public SystemRoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSystemRoleRequest request) {
        return service.updateRole(id, request);
    }

    @PostMapping("/{id}/actions/update-permissions")
    @PreAuthorize("hasAuthority('system.role.manage')")
    @Operation(summary = "配置角色权限")
    public SystemRoleResponse updatePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSystemRolePermissionsRequest request
    ) {
        return service.updateRolePermissions(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.role.manage')")
    @Operation(summary = "删除系统角色")
    public void delete(@PathVariable UUID id) {
        service.deleteRole(id);
    }
}
