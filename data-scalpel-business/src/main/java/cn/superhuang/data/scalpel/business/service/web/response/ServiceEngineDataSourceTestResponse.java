package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "服务引擎成功使用已同步配置连接指定数据源后的识别结果；失败通过 502 ProblemDetail 返回，不改变登记状态。")

public record ServiceEngineDataSourceTestResponse(
        @Schema(description = "被测试的服务引擎数据源登记 UUID；当前管理接口始终有值。")
        UUID registrationId,
        @Schema(description = "服务引擎稳定编码。")
        String engineCode,
        @Schema(description = "数据源 UUID。")
        UUID dataSourceId,
        @Schema(description = "服务引擎识别的数据库方言标识，例如 POSTGRESQL。")
        String databaseType
) {
}
