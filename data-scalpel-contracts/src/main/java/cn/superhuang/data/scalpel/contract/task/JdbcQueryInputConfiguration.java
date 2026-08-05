package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record JdbcQueryInputConfiguration(
        String dataSourceId,
        String sql,
        String outputTableName,
        String analyzedSqlSha256,
        List<CanvasColumnSchema> outputColumns
) {
    public JdbcQueryInputConfiguration {
        outputColumns = outputColumns == null ? List.of() : List.copyOf(outputColumns);
    }
}
