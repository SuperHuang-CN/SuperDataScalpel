package cn.superhuang.data.scalpel.dialect.builtin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Runtime properties that decide whether Dameng DDL can be treated as one rollbackable batch. */
record DamengChangeRuntime(String ddlAutoCommit, String dpcMode, boolean readable) {

    static DamengChangeRuntime read(Connection connection) throws SQLException {
        return new DamengChangeRuntime(
                readParameter(connection, "DDL_AUTO_COMMIT"),
                readParameter(connection, "DPC_MODE"),
                true
        );
    }

    static DamengChangeRuntime unavailable() {
        return new DamengChangeRuntime(null, null, false);
    }

    boolean supportsTransactionalDdl() {
        return readable && "0".equals(normalize(ddlAutoCommit)) && "0".equals(normalize(dpcMode));
    }

    String unsupportedMessage() {
        if (!readable) {
            return "无法读取达梦 DDL_AUTO_COMMIT 或 DPC_MODE 运行参数，平台不会猜测 DDL 事务能力";
        }
        if (!"0".equals(normalize(ddlAutoCommit))) {
            return "达梦当前 DDL_AUTO_COMMIT=" + printable(ddlAutoCommit) + "，多步 DDL 不能由平台保证整体回滚";
        }
        return "达梦当前 DPC_MODE=" + printable(dpcMode) + "，DPC 环境执行 DDL 会自动提交";
    }

    private static String readParameter(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT SF_GET_PARA_VALUE(2, '" + name + "')")) {
            if (!resultSet.next()) {
                throw new SQLException("达梦运行参数查询未返回结果：" + name);
            }
            return resultSet.getString(1);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String printable(String value) {
        return value == null || value.isBlank() ? "未知" : value.trim();
    }
}
