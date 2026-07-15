package cn.superhuang.data.scalpel.business.task.service;

/** Machine-readable validation issue; columnCode is present only for field-level problems. */
public record LocalSqlDefinitionInspectionProblem(String code, String message, String columnCode) {
}
