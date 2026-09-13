package cn.superhuang.data.scalpel.business.system.access.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import cn.superhuang.data.scalpel.business.system.access.web.request.CreateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.ResetSystemUserPasswordRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemUserResponse;
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
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/system/users")
@Tag(name = "系统管理-用户管理")
public class SystemUserResource {

    private final SystemAccessService service;

    public SystemUserResource(SystemAccessService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询系统用户")
    @GetMapping
    @PreAuthorize("hasAuthority('system.user.view')")
    @Operation(summary = "查询系统用户", description = "分页查询用户及其当前角色；响应不包含密码哈希。")
    public PageResponse<SystemUserResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchUsers(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询系统用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system.user.view')")
    @Operation(summary = "查询系统用户详情", description = "返回用户显示信息、绑定角色和启用状态。")
    public SystemUserResponse get(@Parameter(description = "用户 UUID。") @PathVariable UUID id) {
        return service.getUser(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增系统用户")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "新增系统用户", description = "创建用户并保存密码哈希；用户名规范化为小写且必须唯一。")
    public SystemUserResponse create(@Valid @RequestBody CreateSystemUserRequest request) {
        return service.createUser(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改系统用户")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "修改系统用户", description = "整体替换显示名、唯一角色和启用状态；当前登录用户不能停用自己。角色或启用状态变化不会撤销已签发的普通 JWT，该 JWT 仍按签发时权限持续到过期；系统 MCP 令牌下次请求读取最新用户状态和权限。")
    public SystemUserResponse update(
            @Parameter(description = "用户 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateSystemUserRequest request,
            Principal currentUser
    ) {
        return service.updateUser(id, request, currentUser.getName());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "重置系统用户密码")
    @PostMapping("/{id}/actions/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "重置系统用户密码", description = "用新密码哈希替换原密码，成功返回 204；不会撤销已签发的普通 JWT，也不会轮换该用户绑定的系统 MCP 令牌。")
    public void resetPassword(@Parameter(description = "用户 UUID。") @PathVariable UUID id, @Valid @RequestBody ResetSystemUserPasswordRequest request) {
        service.resetUserPassword(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除系统用户")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.user.manage')")
    @Operation(summary = "删除系统用户", description = "永久删除用户；当前登录用户不能删除自己，成功返回 204。删除后绑定该用户的系统 MCP 令牌无法再认证；已签发的普通 JWT 没有服务端撤销检查，会继续有效至过期。")
    public void delete(@Parameter(description = "用户 UUID。") @PathVariable UUID id, Principal currentUser) {
        service.deleteUser(id, currentUser.getName());
    }
}
