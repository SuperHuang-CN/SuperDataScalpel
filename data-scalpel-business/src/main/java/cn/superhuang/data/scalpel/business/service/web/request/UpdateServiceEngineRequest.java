package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "整体修改服务引擎连接、凭据、启用状态和显示信息；引擎类型与编码不可修改。类型专属字段对另一种引擎忽略。")

public record UpdateServiceEngineRequest(
        @Schema(description = "服务引擎显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "引擎管理 API 的基础 URL。")
        @NotBlank @Size(max = 500) String adminUrl,
        @Schema(description = "引擎对外运行时基础 URL。")
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Schema(description = "DATASCALPEL 管理令牌；空值或空白保留原值，GEOSERVER 忽略。非空值会加密保存，响应仅返回是否已配置。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String managementToken,
        @Schema(description = "GeoServer 管理用户名；空值或空白保留原值，DATASCALPEL 忽略。")
        @Size(max = 200) String geoServerUsername,
        @Schema(description = "GeoServer 密码；空值或空白保留原值，DATASCALPEL 忽略。非空值加密保存，响应不返回明文。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String geoServerPassword,
        @Schema(description = "GeoServer 工作区；空值或空白规范化为默认值 datascalpel，而不是保留原值；DATASCALPEL 忽略。")
        @Size(max = 100) String geoServerWorkspace,
        @Schema(description = "是否允许 Admin 后续执行数据源同步、访问策略应用和服务部署；false 不会反部署、停止或撤销远端已有业务服务。")
        boolean enabled,
        @Schema(description = "部署位置、运维负责人或用途说明；传空值表示清除。")
        @Size(max = 1000) String description
) {
    public UpdateServiceEngineRequest(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementToken,
            boolean enabled,
            String description
    ) {
        this(name, adminUrl, runtimeUrl, managementToken, null, null, null, enabled, description);
    }
}
