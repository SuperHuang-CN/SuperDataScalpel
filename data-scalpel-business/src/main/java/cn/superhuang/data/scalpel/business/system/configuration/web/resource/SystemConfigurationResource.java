package cn.superhuang.data.scalpel.business.system.configuration.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import cn.superhuang.data.scalpel.business.system.configuration.domain.PanoramaMapConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.web.request.UpdateSystemConfigurationRequest;
import cn.superhuang.data.scalpel.business.system.configuration.web.response.SystemConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询系统配置", description = "按通用 Search DSL 分页查询数据库中的公开系统配置。响应排除当前代码声明为 internal 的初始化标记，不包含部署密钥或外部 YAML 配置。启动时只插入缺失的代码预置项，不覆盖已有值和元数据；已从代码移除的历史公开记录仍可能保留并出现在结果中。")
    public PageResponse<SystemConfigurationResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @GetMapping("/map-config")
    @PreAuthorize("hasAnyAuthority('model.view', 'panorama.view', 'system.configuration.view')")
    @Operation(summary = "查询地图底图配置", description = "只读返回模型空间预览与全景地图共用的 XYZ 瓦片地址、纯文本署名和最大瓦片缩放级别。沿用 panorama.map 配置键；url 为空表示关闭底图。需要模型查看、全景查看或系统配置查看权限之一；不返回其他系统配置。浏览器直接请求瓦片，服务端不代理。无登录返回 401，权限不足返回 403，配置缺失返回 502。")
    public PanoramaMapConfiguration mapConfiguration() {
        return service.mapConfiguration();
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改系统配置值")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    @Operation(summary = "修改系统配置值", description = "按记录的 valueType 校验并规范化新值；当前代码仍声明的配置还会应用其范围或结构等专属规则。配置键、名称、类型和说明不能修改，internal 配置按不存在处理。已从代码移除但数据库仍保留的公开记录只执行通用类型校验。依赖该配置的业务在下一次读取时使用新值。")
    public SystemConfigurationResponse update(
            @Parameter(description = "配置记录 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateSystemConfigurationRequest request
    ) {
        return service.update(id, request);
    }
}
