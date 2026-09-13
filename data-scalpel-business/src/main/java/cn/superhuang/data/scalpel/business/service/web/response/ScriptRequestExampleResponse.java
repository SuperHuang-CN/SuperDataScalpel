package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "脚本服务工作台和调用说明使用的一份已规范化模拟请求。")

public record ScriptRequestExampleResponse(
        @Schema(description = "请求示例的稳定字符串 ID，在单个脚本服务定义内唯一；不是业务资源 UUID。")
        String id,
        @Schema(description = "请求示例显示名称。")
        String name,
        @Schema(description = "脚本请求示例的原始请求体文本。")
        String bodyText,
        @Schema(description = "按保存顺序返回的模拟查询参数列表。")
        List<ScriptRequestParameterResponse> query,
        @Schema(description = "按保存顺序返回、仅供脚本读取的模拟请求头列表。")
        List<ScriptRequestParameterResponse> headers
) {
}
