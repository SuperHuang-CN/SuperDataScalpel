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
@RestController @RequestMapping("/api/v1/system-mcp")
public class SystemMcpManagementResource {
    private final SystemMcpManagementService management;
    public SystemMcpManagementResource(SystemMcpManagementService management) {
        this.management=management;
    }
    @GetMapping("/configuration") @PreAuthorize("hasAuthority('system.mcp.view')") public SystemMcpConfigurationResponse configuration() {
        return management.configuration();
    }
    @PostMapping("/actions/update-configuration") @PreAuthorize("hasAuthority('system.mcp.update')") public SystemMcpConfigurationResponse update(@Valid @RequestBody UpdateSystemMcpConfigurationRequest r,Principal p) {
        return management.updateConfiguration(r,p.getName());
    }
    @PostMapping("/actions/refresh-catalog") @PreAuthorize("hasAuthority('system.mcp.update')") public SystemMcpConfigurationResponse refresh(Principal p) {
        return management.refreshCatalog(p.getName());
    }
    @GetMapping("/apis") @PreAuthorize("hasAuthority('system.mcp.view')") public PageResponse<SystemMcpApiResponse> apis(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.apis(r);
    }
    @GetMapping("/apis/{id}") @PreAuthorize("hasAuthority('system.mcp.view')") public SystemMcpApiResponse api(@PathVariable UUID id) {
        return management.api(id);
    }
    @GetMapping("/audits") @PreAuthorize("hasAuthority('system.mcp.view')") public PageResponse<SystemMcpAuditResponse> audits(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.audits(r);
    }
    @GetMapping("/access-tokens") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.view')") public PageResponse<SystemMcpTokenResponse> tokens(@ParameterObject @ModelAttribute SearchRequest r) {
        return management.tokens(r);
    }
    @PostMapping("/access-tokens") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpIssuedTokenResponse create(@Valid @RequestBody CreateSystemMcpTokenRequest r,Principal p) {
        return management.createToken(r,p.getName());
    }
    @PostMapping("/access-tokens/{id}/actions/update") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpTokenResponse updateToken(@PathVariable UUID id,@Valid @RequestBody UpdateSystemMcpTokenRequest r,Principal p) {
        return management.updateToken(id,r,p.getName());
    }
    @PostMapping("/access-tokens/{id}/actions/rotate") @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public SystemMcpIssuedTokenResponse rotate(@PathVariable UUID id,Principal p) {
        return management.rotateToken(id,p.getName());
    }
    @PostMapping("/access-tokens/{id}/actions/enable") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void enable(@PathVariable UUID id,Principal p) {
        management.enableToken(id,true,p.getName());
    }
    @PostMapping("/access-tokens/{id}/actions/disable") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void disable(@PathVariable UUID id,Principal p) {
        management.enableToken(id,false,p.getName());
    }
    @PostMapping("/access-tokens/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('super_admin') and hasAuthority('system.mcp.update')") public void delete(@PathVariable UUID id,Principal p) {
        management.deleteToken(id,p.getName());
    }
}
