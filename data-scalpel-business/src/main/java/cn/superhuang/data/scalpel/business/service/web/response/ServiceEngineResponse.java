package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "可承载数据服务部署的运行引擎及其管理连接配置摘要，不返回任何凭据明文。")

public record ServiceEngineResponse(
        @Schema(description = "服务引擎 UUID，用于数据源注册、访问策略和数据服务部署。")
        UUID id,
        @Schema(description = "服务引擎产品类型：DATASCALPEL 承载表、SQL 和脚本服务，GEOSERVER 承载空间服务。")
        ServiceEngineType type,
        @Schema(description = "服务引擎唯一稳定编码。")
        String code,
        @Schema(description = "服务引擎显示名称。")
        String name,
        @Schema(description = "引擎管理 API 的基础 URL。")
        String adminUrl,
        @Schema(description = "引擎对外运行时基础 URL。")
        String runtimeUrl,
        @Schema(description = "是否已保存可用于服务引擎管理 API 的令牌；响应永远不返回令牌明文。")
        boolean managementTokenConfigured,
        @Schema(description = "GeoServer 管理用户名；非 GeoServer 引擎为空。")
        String geoServerUsername,
        @Schema(description = "GeoServer 工作区；非 GeoServer 引擎为空。")
        String geoServerWorkspace,
        @Schema(description = "是否已保存 GeoServer 管理密码；不返回密码明文。")
        boolean geoServerCredentialConfigured,
        @Schema(description = "Admin 管理开关；false 时阻止新的数据源同步、访问策略应用和服务部署等管理动作，但不会调用远端停止已有部署，已发布服务的实际可用性需查看其部署状态和运行端。")
        boolean enabled,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static ServiceEngineResponse from(ServiceEngine engine) {
        return new ServiceEngineResponse(
                engine.getId(), engine.getType(), engine.getCode(), engine.getName(), engine.getAdminUrl(), engine.getRuntimeUrl(),
                engine.getManagementTokenCiphertext() != null && !engine.getManagementTokenCiphertext().isBlank(),
                engine.getGeoServerUsername(), engine.getGeoServerWorkspace(),
                engine.getGeoServerPasswordCiphertext() != null && !engine.getGeoServerPasswordCiphertext().isBlank(),
                engine.isEnabled(), engine.getDescription(), engine.getCreatedAt(), engine.getUpdatedAt()
        );
    }
}
