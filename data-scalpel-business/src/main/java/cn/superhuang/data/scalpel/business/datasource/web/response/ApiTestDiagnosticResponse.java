package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "HTTP API 资源测试失败的受控诊断信息")
public record ApiTestDiagnosticResponse(
        @Schema(description = "最外层异常类型，用于定位失败阶段") String exceptionType,
        @Schema(description = "已脱敏的原始异常消息") String rawMessage,
        @Schema(description = "远端 HTTP 状态；连接失败或请求未发出时为空") Integer httpStatus,
        @Schema(description = "最多 4000 个字符且已按已知连接凭据和运行变量脱敏的远端错误响应预览；无响应正文时为空。脱敏不能识别业务正文中的其他敏感数据") String responsePreview,
        @Schema(description = "已脱敏的异常原因链，按由外到内排列") List<ConnectionTestCauseResponse> causes
) {
    public ApiTestDiagnosticResponse {
        causes = causes == null ? List.of() : List.copyOf(causes);
    }
}
