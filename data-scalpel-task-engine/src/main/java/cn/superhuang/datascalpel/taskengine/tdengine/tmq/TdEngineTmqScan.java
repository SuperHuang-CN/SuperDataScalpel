package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.types.StructType;

public final class TdEngineTmqScan implements Scan {
    private final StructType schema;
    private final TdEngineTmqOptions options;

    TdEngineTmqScan(StructType schema, TdEngineTmqOptions options) {
        this.schema = schema;
        this.options = options;
    }

    @Override
    public StructType readSchema() {
        return schema;
    }

    @Override
    public MicroBatchStream toMicroBatchStream(String checkpointLocation) {
        return new TdEngineTmqMicroBatchStream(schema, options, checkpointLocation);
    }

    @Override
    public String description() {
        return "TDengine TMQ topic " + options.topic();
    }
}
