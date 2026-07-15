package cn.superhuang.data.scalpel.business.task.service;

import java.util.List;

/** Result of an external, read-only validation for a saved local SQL definition. */
public record LocalSqlDefinitionInspection(
        List<LocalSqlDefinitionInspectionProblem> problems,
        List<LocalSqlDefinitionInspectionColumn> columns,
        List<String> targetColumns,
        String generatedInsertSql
) {

    public LocalSqlDefinitionInspection {
        problems = problems == null ? List.of() : List.copyOf(problems);
        columns = columns == null ? List.of() : List.copyOf(columns);
        targetColumns = targetColumns == null ? List.of() : List.copyOf(targetColumns);
    }

    public boolean valid() {
        return problems.isEmpty();
    }
}
