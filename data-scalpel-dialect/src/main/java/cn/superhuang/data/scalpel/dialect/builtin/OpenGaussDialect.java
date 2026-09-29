package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import java.util.List;

/** PostgreSQL-family metadata with openGauss-specific single-row MERGE syntax. */
public final class OpenGaussDialect extends PostgreSqlDialect {

    public OpenGaussDialect() {
        super("OPENGAUSS", "openGauss", 5432, "org.opengauss.Driver", "jdbc:opengauss://");
    }

    @Override
    public String renderRowUpsert(TableIdentifier target, List<JdbcUpsertColumn> columns, List<String> keyColumns) {
        return MergeRowUpsertSupport.renderOpenGauss(this, target, columns, keyColumns);
    }
}
