package cn.superhuang.datascalpel.sdk;

/** Stable entry point implemented by a user Spark JAR. */
public interface SparkBatchJob {
    void execute(SparkJobContext context) throws Exception;
}
