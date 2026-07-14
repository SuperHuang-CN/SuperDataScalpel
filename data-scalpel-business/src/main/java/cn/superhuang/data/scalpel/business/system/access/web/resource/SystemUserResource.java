package cn.superhuang.data.scalpel.business.system.access.web.resource;

import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import cn.superhuang.data.scalpel.business.system.access.web.request.CreateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.ResetSystemUserPasswordRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemUserResponse;
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
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/system/users")
@Tag(name = "系统管理-用户管理")
public class SystemUserResource {

    private final SystemAccessService service;

    public SystemUserResource(SystemAccessService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system.user.view')")
    @Operation(summary = "查询系统用户")
    public PageResponse<SystemUserResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchUsers(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system.user.view')")
    @Operation(summary = "查询系统用户详情")
    public SystemUserResponse get(@PathVariable UUID id) {
        return service.getUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "新增系统用户")
    public SystemUserResponse create(@Valid @RequestBody CreateSystemUserRequest request) {
        return service.createUser(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "修改系统用户")
    public SystemUserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSystemUserRequest request,
            Principal currentUser
    ) {
        return service.updateUser(id, request, currentUser.getName());
    }

    @PostMapping("/{id}/actions/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "重置系统用户密码")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetSystemUserPasswordRequest request) {
        service.resetUserPassword(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "删除系统用户")
    public void delete(@PathVariable UUID id, Principal currentUser) {
        service.deleteUser(id, currentUser.getName());
    }
}
