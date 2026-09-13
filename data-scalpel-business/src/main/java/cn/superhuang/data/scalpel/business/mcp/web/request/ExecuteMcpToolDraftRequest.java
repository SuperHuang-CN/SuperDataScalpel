package cn.superhuang.data.scalpel.business.mcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "在不保存或发布的情况下校验并执行一份 MCP Tool 草稿。Groovy 按可信管理员代码运行，不提供安全沙箱。")

public record ExecuteMcpToolDraftRequest(
        @Schema(description = "工具输入 JSON Schema 文本；根 type 必须为 object，可使用本地引用，不允许远程引用。")
        @NotBlank @Size(max=65536) String inputSchemaJson,
        @Schema(description = "工具输出 JSON Schema 文本；提供时根 type 必须为 object，可使用本地引用，不允许远程引用；为空表示不校验结构化输出。")
        @Size(max=65536) String outputSchemaJson,
        @Schema(description = "要执行的 Groovy 工具脚本草稿，最长 204800 字符；脚本按可信代码运行，可使用平台已提供的脚本能力。")
        @NotBlank @Size(max=204800) String script,
        @Schema(description = "本次草稿执行的 JSON 参数文本，最大 1 MiB；必须通过 inputSchemaJson 校验。")
        @NotBlank @Size(max=1048576) String argumentsJson
) {}
