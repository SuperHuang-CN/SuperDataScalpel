package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
/** A stable response shape for local and later external definition validation. */
@Schema(description = "当前已保存 Local SQL 定义的发布前只读 JDBC 预检结果。可能执行最多返回 1 行的查询以读取元数据，但不会执行生成的 INSERT、保存定义或发布任务。")
public record LocalSqlDefinitionValidationResponse(
        @Schema(description = "是否通过全部阻断性校验。")
        boolean valid,
        @Schema(description = "阻止发布和运行的问题；通过时为空列表。部分问题并不阻止草稿保存，因为保存接口只做本地结构校验。")
        java.util.List<LocalSqlDefinitionValidationProblemResponse> problems,
        @Schema(description = "JDBC 预检识别出的查询结果列；模型或 SQL 在连接前已失败时为空列表，即使 valid=false 也可能包含已识别的列。")
        java.util.List<LocalSqlDefinitionValidationColumnResponse> columns,
        @Schema(description = "若执行写入，生成 INSERT 时会使用的输出模型字段编码，按查询结果顺序排列；预检本身不写入，存在问题时可能只是部分列表。")
        java.util.List<String> targetColumns,
        @Schema(description = "字段血缘覆盖程度枚举名称；由 SQL AST 与声明输入匹配结果计算，无法完整分析时为部分或未知，不影响 valid 的其他校验结论。")
        String lineageCoverage,
        @Schema(description = "SQL AST 血缘分析状态：COMPLETE、PARTIAL 或 UNAVAILABLE。")
        String lineageAnalysisStatus,
        @Schema(description = "不会阻止执行、但会降低血缘完整性的告警。")
        java.util.List<LocalSqlLineageWarningResponse> lineageWarnings
) {
}
