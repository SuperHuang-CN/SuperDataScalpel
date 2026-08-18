package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.streaming.StreamingQuery;

@FunctionalInterface
public interface StreamingQueryStarter {
    StreamingQuery start(StreamingQuerySpec specification) throws Exception;
}
