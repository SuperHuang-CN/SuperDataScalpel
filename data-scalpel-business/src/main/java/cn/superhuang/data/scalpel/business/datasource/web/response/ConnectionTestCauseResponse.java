package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "连接或资源测试异常原因链中的一项")
public record ConnectionTestCauseResponse(
        @Schema(description = "异常类的简单名称，用于区分网络、驱动、认证或协议错误。") String exceptionType,
        @Schema(description = "已裁剪和脱敏的异常消息，不包含凭据。") String message
) {
}
