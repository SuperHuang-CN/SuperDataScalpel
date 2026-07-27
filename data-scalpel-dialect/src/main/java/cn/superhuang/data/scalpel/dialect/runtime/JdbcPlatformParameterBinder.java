package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** JDBC binding for values already converted with PlatformQueryValueConverter. */
public final class JdbcPlatformParameterBinder {

    private JdbcPlatformParameterBinder() {
    }

    public static void bind(PreparedStatement statement, List<SqlQueryParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            bind(statement, index + 1, parameters.get(index));
        }
    }

    private static void bind(PreparedStatement statement, int index, SqlQueryParameter parameter) throws SQLException {
        PlatformDataType type = parameter.typeDefinition().type();
        Object value = parameter.value();
        if (value == null) {
            statement.setNull(index, jdbcType(type));
            return;
        }
        switch (type) {
            case BOOLEAN -> statement.setBoolean(index, (Boolean) value);
            case BYTE -> statement.setByte(index, (Byte) value);
            case SHORT -> statement.setShort(index, (Short) value);
            case INTEGER -> statement.setInt(index, (Integer) value);
            case LONG -> statement.setLong(index, (Long) value);
            case FLOAT -> statement.setFloat(index, (Float) value);
            case DOUBLE -> statement.setDouble(index, (Double) value);
            case DECIMAL -> statement.setBigDecimal(index, (java.math.BigDecimal) value);
            case STRING -> statement.setString(index, (String) value);
            case DATE, TIMESTAMP, TIMESTAMP_NTZ -> statement.setObject(index, value);
            case BINARY -> throw new IllegalArgumentException("BINARY SQL parameters are not supported");
            case GEOMETRY -> throw new IllegalArgumentException("GEOMETRY SQL parameters are not supported");
        }
    }

    private static int jdbcType(PlatformDataType type) {
        return switch (type) {
            case BOOLEAN -> Types.BOOLEAN;
            case BYTE -> Types.TINYINT;
            case SHORT -> Types.SMALLINT;
            case INTEGER -> Types.INTEGER;
            case LONG -> Types.BIGINT;
            case FLOAT -> Types.REAL;
            case DOUBLE -> Types.DOUBLE;
            case DECIMAL -> Types.DECIMAL;
            case STRING -> Types.VARCHAR;
            case DATE -> Types.DATE;
            case TIMESTAMP -> Types.TIMESTAMP_WITH_TIMEZONE;
            case TIMESTAMP_NTZ -> Types.TIMESTAMP;
            case BINARY -> Types.VARBINARY;
            case GEOMETRY -> throw new IllegalArgumentException("GEOMETRY SQL parameters are not supported");
        };
    }
}
