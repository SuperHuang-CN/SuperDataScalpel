package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;

public record ColumnMetadataResponse(
        String name,
        int ordinal,
        int jdbcType,
        String nativeType,
        String logicalType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        boolean generated,
        String comment
) {
    static ColumnMetadataResponse from(ColumnMetadata column) {
        return new ColumnMetadataResponse(
                column.name(), column.ordinal(), column.jdbcType(), column.nativeType(), column.logicalType().name(),
                column.length(), column.precision(), column.scale(), column.nullable(), column.defaultValue(),
                column.autoIncrement(), column.generated(), column.comment()
        );
    }
}
