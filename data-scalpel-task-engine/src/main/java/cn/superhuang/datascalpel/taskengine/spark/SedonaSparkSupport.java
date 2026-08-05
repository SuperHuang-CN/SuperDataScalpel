package cn.superhuang.datascalpel.taskengine.spark;

import org.apache.sedona.spark.SedonaContext;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;

/** Creates every Task Engine Spark session with the same Sedona runtime configuration. */
public final class SedonaSparkSupport {
    public static final String SERIALIZER = "org.apache.spark.serializer.KryoSerializer";
    public static final String REGISTRATOR = "org.apache.sedona.core.serde.SedonaKryoRegistrator";

    private SedonaSparkSupport() {
    }

    public static SparkConf configure(SparkConf sparkConf) {
        sparkConf.set("spark.serializer", SERIALIZER);
        sparkConf.set("spark.kryo.registrator", REGISTRATOR);
        return sparkConf;
    }

    public static SparkSession.Builder builder() {
        return SedonaContext.builder()
                .config("spark.serializer", SERIALIZER)
                .config("spark.kryo.registrator", REGISTRATOR);
    }

    public static SparkSession initialize(SparkSession session) {
        return SedonaContext.create(session);
    }

    public static SparkSession childSession(SparkSession parent) {
        return initialize(parent.newSession());
    }
}
