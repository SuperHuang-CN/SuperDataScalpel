package cn.superhuang.datascalpel.sdk;

/** Stable entry point implemented by a user Structured Streaming JAR. */
public interface SparkStreamingJob {
    void start(SparkStreamingJobContext context) throws Exception;

    default void onStop(SparkStreamingJobContext context) throws Exception {
    }
}
