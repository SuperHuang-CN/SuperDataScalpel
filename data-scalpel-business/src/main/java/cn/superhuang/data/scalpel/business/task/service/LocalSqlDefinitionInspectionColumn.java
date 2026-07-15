package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

/** One output column observed from JDBC during local SQL validation. */
public record LocalSqlDefinitionInspectionColumn(
        int ordinal,
        String label,
        LogicalType logicalType,
        String nativeType,
        boolean nullable,
        String matchedOutputFieldCode
) {
}
