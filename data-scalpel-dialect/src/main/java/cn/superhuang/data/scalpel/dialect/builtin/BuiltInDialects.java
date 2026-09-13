package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;

import java.util.List;

public final class BuiltInDialects {

    private BuiltInDialects() {
    }

    public static DialectRegistry registry() {
        return new DialectRegistry(List.of(
                new PostgreSqlDialect(),
                new HighGoDialect(),
                new MySqlDialect(),
                new OracleDialect(),
                new SqlServerDialect(),
                new ClickHouseDialect(),
                new DamengDialect(),
                new KingbaseDialect(),
                new OpenGaussDialect(),
                new TdEngineDialect(TdEngineJdbcTransport.WEBSOCKET),
                new TdEngineDialect(TdEngineJdbcTransport.RESTFUL)
        ));
    }
}
