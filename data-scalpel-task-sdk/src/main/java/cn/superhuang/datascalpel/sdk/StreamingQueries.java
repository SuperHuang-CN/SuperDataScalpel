package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.streaming.StreamingQuery;

public interface StreamingQueries {
    StreamingQuery start(
            String logicalName,
            StreamingSinkType sinkType,
            StreamingQueryStarter starter
    ) throws Exception;
}
