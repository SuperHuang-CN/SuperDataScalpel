package cn.superhuang.data.scalpel.business.systemmcp.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.service.*;
import cn.superhuang.data.scalpel.business.systemmcp.web.request.*;
import cn.superhuang.data.scalpel.business.systemmcp.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import java.util.*;
import java.security.Principal;
import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/v1/system-mcp")
@Tag(name = "系统 MCP 管理", description = "管理系统 MCP 总开关、接口开放清单、专用令牌和审计记录")
public class SystemMcpManagementResource {
    private final SystemMcpManagementService management;
    public SystemMcpManagementResource(SystemMcpManagementService management) {
        this.management=management;
    }
    @Operation(summary = "查询系统 MCP 配置", description = "返回总开关、公开连接地址、目录同步状态、最近同步错误和接口统计；不会返回访问令牌秘密。目录未就绪时协议入口不可调用。")
    @GetMapping("/configuration") @PreAuthorize("hasAuthority('system.mcp.view')") public SystemMcpConfigurationResponse configuration() {
        return management.configuration();
    }
    @Operation(summary = "保存系统 MCP 配置", description = "原子保存总开关与请求中明确列出的接口开放变更，未提交接口保持原状。保存时重新校验目录就绪及接口可用性，任一目标不能开放则整批不生效。")
    @PostMapping("/actions/update-configuration") @PreAuthorize("hasAuthority('system.mcp.update')") public SystemMcpConfigurationResponse update(@Valid @RequestBody UpdateSystemMcpConfigurationRequest r,Principal p) {
        return management.updateConfiguration(r,p.getName());
    }
    @Operation(summary = "同步系统 MCP 接口目录", description = "重新解析当前部署的 Spring MVC 路由、权限表达式和 OpenAPI 契约；完整解析成功后原子更新目录。新增接口默认关闭，仍受支持的已开放接口保留状态，删除或不再支持的接口关闭。")
    @PostMapping("/actions/refresh-catalog") @PreAuthorize("hasAuthority('system.mcp.update')") public SystemMcpConfigurationResponse refresh(Principal p) {
        return management.refreshCatalog(p.getName());
    }
    @Operation(summary = "查询系统 MCP 接口目录", description = "按通用 Search DSL 分页查询从当前 Spring MVC 与 OpenAPI 投影得到的候选接口，包含稳定 operationId、模块、操作性质、支持状态、原因和开放状态。")
    @GetMapping("/apis") @PreAuthorize("hasAuthority('system.mcp.view')") public PageResponse<SystemMcpApiResponse> apis(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.apis(r);
    }
    @Operation(summary = "查询系统 MCP 接口分类", description = "按 Resource 的 OpenAPI Tag 返回业务模块及接口数量，用于管理页面的分类导航。")
    @GetMapping("/apis/modules") @PreAuthorize("hasAuthority('system.mcp.view')") public List<SystemMcpModuleResponse> modules() {
        return management.modules();
    }
    @Operation(summary = "查询系统 MCP 接口详情", description = "返回一个目录记录的最新请求/响应 Schema、契约指纹、关键词、前置条件、关联接口及支持性诊断；接口契约只能由代码同步，不能在管理页面编辑。")
    @GetMapping("/apis/{id}") @PreAuthorize("hasAuthority('system.mcp.view')") public SystemMcpApiResponse api(@Parameter(description = "接口目录记录 UUID") @PathVariable UUID id) {
        return management.api(id);
    }
    @Operation(summary = "查询系统 MCP 审计记录", description = "按通用 Search DSL 分页查询配置变更、令牌管理和工具调用元数据，包含操作人、绑定用户、接口、耗时、执行状态及错误分类；不记录请求体、响应正文或完整令牌。")
    @GetMapping("/audits") @PreAuthorize("hasAuthority('system.mcp.view')") public PageResponse<SystemMcpAuditResponse> audits(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.audits(r);
    }
    @Operation(summary = "查询系统 MCP 访问令牌", description = "仅超级管理员可分页查询专用令牌的名称、绑定用户、状态、轮换版本、过期时间和最近使用时间；数据库与响应均不提供可恢复的旧秘密。")
    @GetMapping("/access-tokens") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.view')") public PageResponse<SystemMcpTokenResponse> tokens(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.tokens(r);
    }
    @Operation(summary = "创建系统 MCP 访问令牌", description = "仅超级管理员可为一个当前存在的系统用户创建 dssmcp_ 专用令牌。数据库只保存 SHA-256 摘要，完整秘密只在本次禁止缓存的成功响应中返回一次。")
    @PostMapping("/access-tokens") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpIssuedTokenResponse create(@Valid @RequestBody CreateSystemMcpTokenRequest r,Principal p) {
        return management.createToken(r,p.getName());
    }
    @Operation(summary = "修改系统 MCP 访问令牌", description = "修改令牌显示名称和过期时间，不改变秘密、绑定用户、启用状态或轮换版本；新过期时间在后续请求中立即生效。")
    @PostMapping("/access-tokens/{id}/actions/update") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpTokenResponse updateToken(@Parameter(description = "访问令牌 UUID") @PathVariable UUID id,@Valid @RequestBody UpdateSystemMcpTokenRequest r,Principal p) {
        return management.updateToken(id,r,p.getName());
    }
    @Operation(summary = "轮换系统 MCP 访问令牌", description = "为现有令牌生成新秘密并递增轮换版本，旧秘密立即失效；绑定用户、名称、状态和过期时间保持不变，新秘密只在本次成功响应中返回一次。")
    @PostMapping("/access-tokens/{id}/actions/rotate") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpIssuedTokenResponse rotate(@Parameter(description = "访问令牌 UUID") @PathVariable UUID id,Principal p) {
        return management.rotateToken(id,p.getName());
    }
    @Operation(summary = "启用系统 MCP 访问令牌", description = "恢复该令牌后续认证能力；仍需满足未过期、绑定用户启用、系统总开关开启和具体接口已开放等条件。成功返回 204。")
    @PostMapping("/access-tokens/{id}/actions/enable") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void enable(@Parameter(description = "访问令牌 UUID") @PathVariable UUID id,Principal p) {
        management.enableToken(id,true,p.getName());
    }
    @Operation(summary = "停用系统 MCP 访问令牌", description = "使该令牌的后续系统 MCP 请求立即认证失败，同时保留元数据和审计记录。成功返回 204。")
    @PostMapping("/access-tokens/{id}/actions/disable") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void disable(@Parameter(description = "访问令牌 UUID") @PathVariable UUID id,Principal p) {
        management.enableToken(id,false,p.getName());
    }
    @Operation(summary = "删除系统 MCP 访问令牌", description = "永久删除令牌摘要和元数据，使后续认证立即失败；既有审计记录继续保留脱敏令牌标识。成功返回 204。")
    @PostMapping("/access-tokens/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void delete(@Parameter(description = "访问令牌 UUID") @PathVariable UUID id,Principal p) {
        management.deleteToken(id,p.getName());
    }
}
