package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.DataStreamWriter;

public interface KafkaResources {
    Dataset<Row> readStream(String bindingName, KafkaStartingOffsets startingOffsets);

    DataStreamWriter<Row> writeStream(String bindingName, Dataset<Row> source);
}
