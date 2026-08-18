package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;

public record ColumnMetadataResponse(
        String name,
        int ordinal,
        int jdbcType,
        String nativeType,
        String logicalType,
        PlatformTypeDefinition platformTypeDefinition,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        boolean generated,
        String comment,
        String role
) {
    static ColumnMetadataResponse from(ColumnMetadata column, DatabaseDialect dialect) {
        var mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(column));
        return new ColumnMetadataResponse(
                column.name(), column.ordinal(), column.jdbcType(), column.nativeType(), column.logicalType().name(),
                mapping.acceptable() ? mapping.definition() : null,
                column.length(), column.precision(), column.scale(), column.nullable(), column.defaultValue(),
                column.autoIncrement(), column.generated(), column.comment(), column.role().name()
        );
    }
}
