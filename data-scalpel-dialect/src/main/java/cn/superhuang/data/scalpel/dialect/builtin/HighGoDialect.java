package cn.superhuang.data.scalpel.dialect.builtin;

public final class HighGoDialect extends PostgreSqlDialect {
    public HighGoDialect() {
        super("HIGHGO", "HighGo", 5866, "com.highgo.jdbc.Driver", "jdbc:highgo://");
    }
}
