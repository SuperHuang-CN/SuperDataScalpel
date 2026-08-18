package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.types.StructType;

final class JdbcIncrementalScan implements Scan {
    private final StructType schema;
    private final JdbcIncrementalOptions options;

    JdbcIncrementalScan(StructType schema, JdbcIncrementalOptions options) {
        this.schema = schema;
        this.options = options;
    }

    @Override
    public StructType readSchema() { return schema; }

    @Override
    public MicroBatchStream toMicroBatchStream(String checkpointLocation) {
        return new JdbcIncrementalMicroBatchStream(schema, options);
    }

    @Override
    public String description() {
        return "JDBC incremental table " + options.tableName();
    }
}
