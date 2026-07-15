package cn.superhuang.data.scalpel.business.task.service;

/** External JDBC validation boundary for a saved local SQL definition. */
public interface LocalSqlDefinitionInspectionPort {

    LocalSqlDefinitionInspection inspect(LocalSqlDefinitionInspectionRequest request);
}
