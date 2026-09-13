package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建或整体保存原 MCP 在线开发平台中的 Groovy Tool 草稿。")

public record SaveMcpToolRequest(
        @Schema(description = "Tool 在所属 Server 内稳定唯一的协议名称。")
        @NotBlank @Size(max=64) @Pattern(regexp="[a-z][a-z0-9_-]{1,63}") String code,
        @Schema(description = "供管理页面显示的 Tool 名称。")
        @NotBlank @Size(max=100) String name,
        @Schema(description = "提供给 MCP 客户端和智能体的 Tool 用途说明。")
        @Size(max=1000) String description,
        @Schema(description = "工具输入 JSON Schema 文本；根 type 必须为 object，可使用本地引用，不允许远程引用。")
        @NotBlank @Size(max=65536) String inputSchemaJson,
        @Schema(description = "工具输出 JSON Schema 文本；提供时根 type 必须为 object，可使用本地引用，不允许远程引用；为空表示不声明结构化输出。")
        @Size(max=65536) String outputSchemaJson,
        @Schema(description = "Tool 调用时执行的 Groovy 脚本，最长 204800 字符；按可信管理员代码运行，不提供安全沙箱。")
        @NotBlank @Size(max=204800) String script,
        @Schema(description = "工具调用示例 JSON 数组文本，最多 10 项；为空表示没有示例。")
        @Size(max=65536) String examplesJson,
        @Schema(description = "是否纳入下次发布快照；false 的草稿 Tool 不会出现在已发布工具列表。")
        boolean enabled,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        @Min(0) @Max(100000) int sortOrder,
        @Schema(description = "修改时可提交客户端读取到的修订号，与服务端不一致则拒绝覆盖；创建时可为空且不会作为初始版本。")
        @Min(0) Long expectedRevision
) {}
