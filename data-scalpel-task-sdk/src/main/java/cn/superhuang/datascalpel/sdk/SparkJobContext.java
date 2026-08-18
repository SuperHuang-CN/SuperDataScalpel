package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.SparkSession;

public interface SparkJobContext {
    SparkSession spark();

    SparkJobIdentity identity();

    SparkJobParameters parameters();

    ModelResources models();

    JdbcResources jdbc();

    JobObservability observability();
}
