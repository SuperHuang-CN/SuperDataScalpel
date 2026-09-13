package cn.superhuang.data.scalpel.dialect.builtin;

/** openGauss keeps its vendor identity while using the PostgreSQL-family capability set. */
public final class OpenGaussDialect extends PostgreSqlDialect {

    public OpenGaussDialect() {
        super("OPENGAUSS", "openGauss", 5432, "org.opengauss.Driver", "jdbc:opengauss://");
    }
}
