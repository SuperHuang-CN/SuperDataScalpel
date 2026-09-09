package cn.superhuang.data.scalpel.business.system.configuration.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.business.system.configuration.web.request.UpdateSystemConfigurationRequest;
import cn.superhuang.data.scalpel.business.system.configuration.web.response.SystemConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/configurations")
@Tag(name = "系统管理-系统配置")
public class SystemConfigurationResource {

    private final SystemConfigurationService service;

    public SystemConfigurationResource(SystemConfigurationService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询系统配置")
    @GetMapping
    @PreAuthorize("hasAuthority('system.configuration.view')")
    @Operation(summary = "查询系统配置")
    public PageResponse<SystemConfigurationResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改系统配置值")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "修改系统配置值")
    public SystemConfigurationResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSystemConfigurationRequest request
    ) {
        return service.update(id, request);
    }
}
