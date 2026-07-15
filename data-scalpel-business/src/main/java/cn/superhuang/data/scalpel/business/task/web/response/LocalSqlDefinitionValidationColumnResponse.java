package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

public record LocalSqlDefinitionValidationColumnResponse(
        int ordinal,
        String label,
        LogicalType logicalType,
        String nativeType,
        boolean nullable,
        String matchedOutputFieldCode
) {
}
