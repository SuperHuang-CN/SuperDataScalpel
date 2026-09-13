package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "脚本服务工作台和调用说明使用的一份完整模拟请求；不构成运行时输入校验规则。")

public record ScriptRequestExampleRequest(
        @Schema(description = "请求示例的稳定字符串 ID，在单个脚本服务定义内唯一；不是业务资源 UUID。")
        @NotBlank @Size(max = 64) String id,
        @Schema(description = "请求示例显示名称，在单个脚本服务定义内唯一。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "脚本请求示例的 JSON 文本，最长 200000 字符；保存时去除首尾空白，空白内容规范化为 {}，非法 JSON 会拒绝整次保存。")
        @NotBlank @Size(max = 200_000) String bodyText,
        @Schema(description = "查询参数列表，最多 100 项；ID 和键分别必须唯一，查询参数键按大小写区分。")
        @NotNull @Size(max = 100) List<@Valid ScriptRequestParameterRequest> query,
        @Schema(description = "模拟请求头列表，最多 100 项；ID 必须唯一，请求头名称按大小写不敏感规则保持唯一，仅供脚本读取。")
        @NotNull @Size(max = 100) List<@Valid ScriptRequestParameterRequest> headers
) {
}
