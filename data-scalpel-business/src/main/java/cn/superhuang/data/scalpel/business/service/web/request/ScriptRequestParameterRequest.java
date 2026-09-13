package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "脚本服务调用示例中的一个查询参数或模拟请求头。")

public record ScriptRequestParameterRequest(
        @Schema(description = "该示例内用于前端编辑列表的稳定参数 ID；同一 query 或 headers 列表内不能重复，不是业务资源 UUID。")
        @NotBlank @Size(max = 64) String id,
        @Schema(description = "查询参数名称或模拟请求头名称，例如 page 或 Content-Type；保存时去除首尾空白。")
        @NotBlank @Size(max = 200) String key,
        @Schema(description = "该示例中参数或请求头的字符串值；允许空字符串，最长 10000 字符。")
        @NotNull @Size(max = 10_000) String value
) {
}
