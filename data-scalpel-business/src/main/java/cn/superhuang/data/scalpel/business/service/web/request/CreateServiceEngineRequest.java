package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "登记一个用于部署和运行数据服务的 DataScalpel Service Engine 或 GeoServer；创建前会同步探测管理端，远端不可达时不保存记录。")

public record CreateServiceEngineRequest(
        @Schema(description = "服务引擎产品类型：DATASCALPEL 承载表、SQL 和脚本服务，GEOSERVER 承载空间服务。")
        @NotNull ServiceEngineType type,
        @Schema(description = "服务引擎稳定技术编码；DATASCALPEL 忽略请求值并使用远端 /info 返回的编码，GEOSERVER 必须显式提供。保存时转为小写，须以字母开头且只含字母、数字和下划线。")
        @Size(max = 64) String code,
        @Schema(description = "服务引擎显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "引擎管理 API 的 HTTP(S) 基础 URL；创建时实际连接该地址进行身份探测。")
        @NotBlank @Size(max = 500) String adminUrl,
        @Schema(description = "引擎对外业务服务的 HTTP(S) 基础 URL；创建 DataScalpel 引擎时只校验 URL 格式，不探测该地址，GeoServer 创建探测会同时规范化该地址。")
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Schema(description = "DataScalpel Service Engine 管理令牌；DATASCALPEL 必填并用于管理端探测，GEOSERVER 忽略。创建后加密保存，不再返回明文。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String managementToken,
        @Schema(description = "GeoServer 管理用户名；GEOSERVER 必填，DATASCALPEL 忽略。")
        @Size(max = 200) String geoServerUsername,
        @Schema(description = "GeoServer 管理密码；GEOSERVER 必填，DATASCALPEL 忽略。创建后加密保存，不再返回明文。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String geoServerPassword,
        @Schema(description = "GeoServer 工作区；GEOSERVER 使用，空值默认 datascalpel，保存为小写；DATASCALPEL 忽略。")
        @Size(max = 100) String geoServerWorkspace,
        @Schema(description = "是否允许 Admin 后续执行数据源同步、访问策略应用和服务部署；省略时为 true。false 不会停止远端已有部署。")
        Boolean enabled,
        @Schema(description = "部署位置、运维负责人或用途说明；未填写时为空。")
        @Size(max = 1000) String description
) {
    public CreateServiceEngineRequest(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementToken,
            Boolean enabled,
            String description
    ) {
        this(
                ServiceEngineType.DATASCALPEL, null, name, adminUrl, runtimeUrl,
                managementToken, null, null, null, enabled, description
        );
    }
}
