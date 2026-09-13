package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "使用尚未保存的连接参数测试服务引擎。")

public record TestServiceEngineRequest(
        @Schema(description = "要测试的服务引擎产品类型：DATASCALPEL 或 GEOSERVER，决定连接地址和认证方式的校验逻辑。")
        @NotNull ServiceEngineType type,
        @Schema(description = "实例编码。GEOSERVER 必填并作为期望编码；DATASCALPEL 忽略，测试结果使用远端 /info 报告的编码。")
        @Size(max = 64) String code,
        @Schema(description = "引擎管理 API 的基础 URL。")
        @NotBlank @Size(max = 500) String adminUrl,
        @Schema(description = "引擎对外运行时基础 URL；GEOSERVER 测试会规范化并检查相关管理能力，DATASCALPEL 测试不使用该字段。")
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Schema(description = "DATASCALPEL 测试使用的管理令牌，必填；GEOSERVER 忽略。不会保存或返回。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String managementToken,
        @Schema(description = "本次测试使用的 GeoServer 管理用户名。")
        @Size(max = 200) String geoServerUsername,
        @Schema(description = "GEOSERVER 测试使用的管理密码，必填；DATASCALPEL 忽略。不会保存或返回。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String geoServerPassword,
        @Schema(description = "GEOSERVER 测试使用的工作区；空值默认 datascalpel。DATASCALPEL 忽略。")
        @Size(max = 100) String geoServerWorkspace
) {
    public TestServiceEngineRequest(String adminUrl, String managementToken) {
        this(
                ServiceEngineType.DATASCALPEL, null, adminUrl, adminUrl,
                managementToken, null, null, null
        );
    }
}
