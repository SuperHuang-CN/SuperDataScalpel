package cn.superhuang.data.scalpel.dialect.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class JdbcConnectionFactory {

    public Connection open(JdbcConnectionSpec spec) throws SQLException, ClassNotFoundException {
        Class.forName(spec.driverClassName());
        Connection connection = DriverManager.getConnection(spec.jdbcUrl(), spec.properties());
        try {
            if (spec.schemaName() != null) {
                connection.setSchema(spec.schemaName());
            }
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    public boolean isDriverAvailable(String driverClassName) {
        try {
            Class.forName(driverClassName);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }
}
