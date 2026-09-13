package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据源连接测试失败的受控诊断信息")
public record ConnectionTestDiagnosticResponse(
        @Schema(description = "最外层异常类型，用于定位失败阶段") String exceptionType,
        @Schema(description = "已脱敏的原始异常消息") String rawMessage,
        @Schema(description = "JDBC 驱动返回的 SQLState；非 JDBC 失败或驱动未提供时为空") String sqlState,
        @Schema(description = "数据库厂商错误码；非 JDBC 失败时为空") Integer vendorCode,
        @Schema(description = "远端 HTTP 状态；非 HTTP 数据源或请求未得到响应时为空") Integer httpStatus,
        @Schema(description = "已截断并脱敏的远端响应预览；无响应正文时为空") String responsePreview,
        @Schema(description = "已脱敏的异常原因链，按由外到内排列") List<ConnectionTestCauseResponse> causes
) {
    public ConnectionTestDiagnosticResponse {
        causes = causes == null ? List.of() : List.copyOf(causes);
    }
}
