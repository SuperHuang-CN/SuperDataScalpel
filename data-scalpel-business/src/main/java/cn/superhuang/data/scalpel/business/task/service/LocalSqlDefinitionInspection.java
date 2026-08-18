package cn.superhuang.data.scalpel.business.task.service;

import java.util.List;

/** Result of an external, read-only validation for a saved local SQL definition. */
public record LocalSqlDefinitionInspection(
        List<LocalSqlDefinitionInspectionProblem> problems,
        List<LocalSqlDefinitionInspectionColumn> columns,
        List<String> targetColumns,
        String generatedInsertSql,
        LocalSqlLineageEvidence lineageEvidence
) {

    public LocalSqlDefinitionInspection {
        problems = problems == null ? List.of() : List.copyOf(problems);
        columns = columns == null ? List.of() : List.copyOf(columns);
        targetColumns = targetColumns == null ? List.of() : List.copyOf(targetColumns);
        lineageEvidence = lineageEvidence == null
                ? LocalSqlLineageEvidence.unavailable("SQL_AST_NOT_ANALYZED", "尚未执行 SQL AST 血缘分析")
                : lineageEvidence;
    }

    public LocalSqlDefinitionInspection(
            List<LocalSqlDefinitionInspectionProblem> problems,
            List<LocalSqlDefinitionInspectionColumn> columns,
            List<String> targetColumns,
            String generatedInsertSql
    ) {
        this(problems, columns, targetColumns, generatedInsertSql, null);
    }

    public boolean valid() {
        return problems.isEmpty();
    }
}
