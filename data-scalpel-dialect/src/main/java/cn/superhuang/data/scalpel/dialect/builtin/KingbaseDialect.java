package cn.superhuang.data.scalpel.dialect.builtin;

import java.sql.Connection;
import java.sql.SQLException;

/** Kingbase R8/R9 keeps one JDBC contract and adapts its catalog vocabulary at runtime. */
public final class KingbaseDialect extends PostgreSqlDialect {

    public KingbaseDialect() {
        super("KINGBASE", "人大金仓", 54321, "com.kingbase8.Driver", "jdbc:kingbase8://");
    }

    @Override
    protected PostgreSqlCatalogNames catalogNames(Connection connection) throws SQLException {
        return PostgreSqlCatalogNames.detectKingbase(connection);
    }
}
