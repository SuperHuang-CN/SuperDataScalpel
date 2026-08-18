package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import org.apache.spark.sql.connector.read.InputPartition;
import org.apache.spark.sql.types.StructType;

import java.util.Map;

public record TdEngineTmqInputPartition(
        TdEngineTmqOptions options,
        StructType schema,
        String groupId,
        Map<Integer, Long> startOffsets,
        Map<Integer, Long> endOffsets,
        TdEngineTmqConsumerFactory consumerFactory
) implements InputPartition {
    public TdEngineTmqInputPartition {
        startOffsets = Map.copyOf(startOffsets);
        endOffsets = Map.copyOf(endOffsets);
    }

    @Override
    public String toString() {
        return "TdEngineTmqInputPartition["
                + "dataSourceId=" + options.dataSourceId()
                + ", topic=" + options.topic()
                + ", vGroupCount=" + startOffsets.size()
                + ']';
    }
}
