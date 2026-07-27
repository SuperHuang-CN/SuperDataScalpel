package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Converts ResultSetMetaData into the portable query inspection model. */
public final class JdbcQueryMetadata {

    private static final Set<Integer> CHARACTER_TYPES = Set.of(
            Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR
    );
    private static final Set<Integer> NUMERIC_TYPES = Set.of(
            Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
            Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE
    );

    private JdbcQueryMetadata() {
    }

    public static QueryInspection inspect(ResultSetMetaData metadata, DatabaseDialect dialect) throws SQLException {
        List<QueryColumn> columns = new ArrayList<>(metadata.getColumnCount());
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            int jdbcType = metadata.getColumnType(index);
            String nativeType = metadata.getColumnTypeName(index);
            LogicalType logicalType = dialect.logicalType(jdbcType, nativeType);
            JdbcTypeDescriptor descriptor = new JdbcTypeDescriptor(
                    jdbcType,
                    nativeType,
                    CHARACTER_TYPES.contains(jdbcType) ? positive(metadata.getColumnDisplaySize(index)) : null,
                    NUMERIC_TYPES.contains(jdbcType) ? positive(metadata.getPrecision(index)) : null,
                    NUMERIC_TYPES.contains(jdbcType) ? Math.max(0, metadata.getScale(index)) : null,
                    NUMERIC_TYPES.contains(jdbcType) ? metadata.isSigned(index) : null
            );
            columns.add(new QueryColumn(
                    metadata.getColumnLabel(index), jdbcType, nativeType, logicalType,
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls, descriptor
            ));
        }
        return new QueryInspection(columns);
    }

    private static Integer positive(int value) {
        return value > 0 ? value : null;
    }
}
