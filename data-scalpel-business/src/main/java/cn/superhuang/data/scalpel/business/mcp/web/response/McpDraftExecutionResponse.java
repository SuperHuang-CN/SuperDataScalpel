package cn.superhuang.data.scalpel.business.mcp.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "MCP Tool 草稿的 Groovy 执行结果、耗时和截断日志。脚本按可信管理员代码运行，不提供安全沙箱。")

public record McpDraftExecutionResponse(
        @Schema(description = "输入 Schema 校验、脚本执行和声明的输出 Schema 校验是否全部成功。false 时查看 error，部分字段可能为空。")
        boolean success,
        @Schema(description = "脚本返回并转换后的结构化 JSON 内容；脚本未返回结构化值或执行失败时为空。")
        Object structuredContent,
        @Schema(description = "脚本返回的文本内容；没有文本结果或执行失败时为空。")
        String text,
        @Schema(description = "执行耗时，单位毫秒。")
        long durationMillis,
        @Schema(description = "脚本校验或执行失败的安全错误说明；success 为 true 时为空。")
        String error,
        @Schema(description = "本次草稿执行捕获的日志，最多 100 行；脚本作者不得写入秘密或敏感业务数据。")
        java.util.List<String> logs
) {}
