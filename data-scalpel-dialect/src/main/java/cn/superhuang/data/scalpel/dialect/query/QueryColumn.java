package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

/** A single output column reported by JDBC for a validated local SQL query. */
public record QueryColumn(
        String label,
        int jdbcType,
        String nativeType,
        LogicalType logicalType,
        boolean nullable
) {

    public QueryColumn {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Query column label is required");
        }
        if (logicalType == null) {
            throw new IllegalArgumentException("Query column logical type is required");
        }
        label = label.trim();
    }
}
