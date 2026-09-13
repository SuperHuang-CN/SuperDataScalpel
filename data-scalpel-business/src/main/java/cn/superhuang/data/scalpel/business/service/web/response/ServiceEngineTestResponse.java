package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;

import java.util.List;

@Schema(description = "服务引擎管理端连接测试结果。DATASCALPEL 只探测 adminUrl 的 /info；GEOSERVER 同时发现版本、工作区相关管理能力和规范化运行地址。")

public record ServiceEngineTestResponse(
        @Schema(description = "本次连接测试使用的服务引擎产品类型。")
        ServiceEngineType type,
        @Schema(description = "服务引擎实例报告的稳定编码。")
        String code,
        @Schema(description = "服务引擎实例报告的软件或管理协议版本；当前 DATASCALPEL 测试固定为空，GEOSERVER 返回发现到的版本。")
        String version,
        @Schema(description = "该引擎声明支持的数据源数据库类型编码列表。")
        List<String> databaseTypes,
        @Schema(description = "运行能力编码列表；当前 DATASCALPEL 固定返回 STANDARD_TABLE、SQL_QUERY、SCRIPT_API，GEOSERVER 返回探测到的空间发布能力。")
        List<String> capabilities,
        @Schema(description = "测试实际使用并规范化的引擎管理 API 基础 URL。")
        String normalizedAdminUrl,
        @Schema(description = "测试后规范化的服务查询运行时基础 URL；DATASCALPEL 测试不探测 runtimeUrl，因此为空。")
        String normalizedRuntimeUrl,
        @Schema(description = "执行耗时，单位毫秒。")
        long elapsedMs
) {
    public ServiceEngineTestResponse(String code, List<String> databaseTypes, long elapsedMs) {
        this(
                ServiceEngineType.DATASCALPEL, code, null, databaseTypes,
                List.of(), null, null, elapsedMs
        );
    }
}
