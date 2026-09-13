package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "阻止 Local SQL 发布或运行的稳定预检问题；草稿保存只执行较小的本地结构校验，因此某些问题不会阻止保存。")

public record LocalSqlDefinitionValidationProblemResponse(
        @Schema(description = "稳定校验问题码，用于识别 SQL、输入模型、参数或输出字段错误。")
        String code,
        @Schema(description = "说明 Local SQL、输入模型、参数或输出字段违反哪项定义规则的可读信息。")
        String message,
        @Schema(description = "问题关联的模型字段编码；全局问题为空。")
        String columnCode
) {
}
