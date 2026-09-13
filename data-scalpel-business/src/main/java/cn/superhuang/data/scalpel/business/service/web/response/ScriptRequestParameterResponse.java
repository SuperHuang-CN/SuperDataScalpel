package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "脚本调用示例中的一个查询参数或模拟请求头。")

public record ScriptRequestParameterResponse(
        @Schema(description = "示例参数的稳定字符串 ID，用于前端编辑列表；不是业务资源 UUID。")
        String id,
        @Schema(description = "请求参数名称或允许的请求头名称。")
        String key,
        @Schema(description = "该调用示例中保存的参数或请求头字符串值。")
        String value
) {
}
