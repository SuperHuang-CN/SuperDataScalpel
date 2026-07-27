package cn.superhuang.datascalpel.taskengine.spark;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;

public final class SparkCompilationScope implements AutoCloseable {
    private final SparkSession session;
    private final JavaSparkContext sparkContext;

    SparkCompilationScope(SparkSession session, JavaSparkContext sparkContext) {
        this.session = session;
        this.sparkContext = sparkContext;
    }

    public SparkSession session() {
        return session;
    }

    @Override
    public void close() {
        sparkContext.clearJobGroup();
    }
}
