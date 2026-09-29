package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Database-native staging operations. Spark and task lifecycle do not belong here. */
public interface JdbcBatchWriteStrategy {
    void validateTarget(Connection connection, TableIdentifier target, boolean overwrite) throws SQLException;
    String createStage(TableIdentifier stage, TableIdentifier target, List<String> columns, String attemptColumn);
    String createWinners(TableIdentifier winners, String attemptColumn);
    String merge(TableIdentifier target, List<String> columns, List<String> keys, String selectedRows);
}
