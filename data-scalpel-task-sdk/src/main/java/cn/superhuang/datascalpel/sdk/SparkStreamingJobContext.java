package cn.superhuang.datascalpel.sdk;

public interface SparkStreamingJobContext extends SparkJobContext {
    KafkaResources kafka();

    StreamingQueries queries();
}
