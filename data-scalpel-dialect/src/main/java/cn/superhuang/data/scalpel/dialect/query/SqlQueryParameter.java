package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

/** One already-converted platform parameter in JDBC placeholder order. */
public record SqlQueryParameter(Object value, PlatformTypeDefinition typeDefinition) {

    public SqlQueryParameter {
        if (typeDefinition == null) {
            throw new IllegalArgumentException("SQL query parameter type is required");
        }
    }
}
