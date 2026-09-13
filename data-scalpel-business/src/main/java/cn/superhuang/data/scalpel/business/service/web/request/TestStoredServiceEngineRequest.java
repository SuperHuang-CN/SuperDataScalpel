package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "测试已保存的服务引擎；适用于当前引擎类型的非空字段临时覆盖已保存连接参数，不写回数据库。请求体可省略。")

public record TestStoredServiceEngineRequest(
        @Schema(description = "引擎管理 API 的基础 URL。")
        @Size(max = 500) String adminUrl,
        @Schema(description = "临时运行时 URL；仅 GEOSERVER 测试使用，DATASCALPEL 测试忽略。空值或空白使用已保存值。")
        @Size(max = 500) String runtimeUrl,
        @Schema(description = "DATASCALPEL 测试临时使用的管理令牌；空值或空白使用已保存秘密，GEOSERVER 忽略。不会写回或返回。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String managementToken,
        @Schema(description = "本次测试临时使用的 GeoServer 管理用户名；为空时使用已保存值。")
        @Size(max = 200) String geoServerUsername,
        @Schema(description = "GEOSERVER 测试临时使用的密码；空值或空白使用已保存秘密，DATASCALPEL 忽略。不会写回或返回。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String geoServerPassword,
        @Schema(description = "本次测试临时使用的 GeoServer 工作区；为空时使用已保存值。")
        @Size(max = 100) String geoServerWorkspace
) {
    public TestStoredServiceEngineRequest(String adminUrl, String managementToken) {
        this(adminUrl, null, managementToken, null, null, null);
    }
}
