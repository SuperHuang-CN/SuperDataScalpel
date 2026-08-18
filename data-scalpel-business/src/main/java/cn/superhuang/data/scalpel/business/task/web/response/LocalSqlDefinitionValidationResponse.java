package cn.superhuang.data.scalpel.business.task.web.response;

/** A stable response shape for local and later external definition validation. */
public record LocalSqlDefinitionValidationResponse(
        boolean valid,
        java.util.List<LocalSqlDefinitionValidationProblemResponse> problems,
        java.util.List<LocalSqlDefinitionValidationColumnResponse> columns,
        java.util.List<String> targetColumns,
        String lineageCoverage,
        String lineageAnalysisStatus,
        java.util.List<LocalSqlLineageWarningResponse> lineageWarnings
) {
}
