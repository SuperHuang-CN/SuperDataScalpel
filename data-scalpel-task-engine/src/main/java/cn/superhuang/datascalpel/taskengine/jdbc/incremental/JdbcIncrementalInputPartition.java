package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.types.StructType;

record JdbcIncrementalInputPartition(
        JdbcIncrementalOptions options,
        StructType schema,
        JdbcIncrementalOffset startOffset,
        JdbcIncrementalOffset endOffset
) implements InputPartition {
    @Override
    public String toString() {
        return "JdbcIncrementalInputPartition[dataSourceId=" + options.dataSourceId()
                + ", tableName=" + options.tableName()
                + ", lowerUnbounded=" + startOffset.lowerUnbounded()
                + ", toTime=" + endOffset.endTime() + ']';
    }
}
