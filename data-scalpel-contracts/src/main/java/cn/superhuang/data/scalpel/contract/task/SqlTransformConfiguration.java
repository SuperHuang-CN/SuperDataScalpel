package cn.superhuang.data.scalpel.contract.task;

/** A single read-only Spark SQL query over the current Canvas table map. */
public record SqlTransformConfiguration(
        String outputTableName,
        String sql
) {
    public SqlTransformConfiguration {
        outputTableName = outputTableName == null ? "" : outputTableName;
        sql = sql == null ? "" : sql;
    }
}
