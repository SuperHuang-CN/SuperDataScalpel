package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.*;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkStreamingJobTestKitTest {
    @Test
    void processesFakeKafkaAndStopsManagedQueries() {
        StructType targetSchema = new StructType().add("order_id", DataTypes.StringType, false);
        AtomicInteger stops = new AtomicInteger();
        SparkStreamingJob job = new SparkStreamingJob() {
            @Override
            public void start(SparkStreamingJobContext context) throws Exception {
                Dataset<Row> input = context.kafka().readStream("source", KafkaStartingOffsets.LATEST);
                context.queries().start("kafka-output", StreamingSinkType.KAFKA, spec ->
                        context.kafka().writeStream("target", input.selectExpr("key", "value"))
                                .queryName(spec.queryName())
                                .option("checkpointLocation", spec.checkpointLocation())
                                .trigger(Trigger.ProcessingTime("50 milliseconds"))
                                .start());
                context.queries().start("model-output", StreamingSinkType.JDBC, spec ->
                        input.selectExpr("CAST(value AS STRING) AS order_id").writeStream()
                                .queryName(spec.queryName())
                                .option("checkpointLocation", spec.checkpointLocation())
                                .foreachBatch((Dataset<Row> batch, Long batchId) -> {
                                    context.models().write("model", batch)
                                            .mode(ModelWriteMode.UPSERT)
                                            .map("order_id", "order_id")
                                            .execute();
                                })
                                .trigger(Trigger.ProcessingTime("50 milliseconds"))
                                .start());
            }

            @Override public void onStop(SparkStreamingJobContext context) { stops.incrementAndGet(); }
        };

        try (TestKafkaTopic source = TestKafkaTopic.create("source-topic");
             TestKafkaTopic target = TestKafkaTopic.create("target-topic");
             SparkStreamingJobTestRun run = SparkStreamingJobTestKit.builder()
                     .kafkaInput("source", source)
                     .kafkaOutput("target", target)
                     .modelOutput("model", TestModelTarget.builder(targetSchema)
                             .primaryKeyColumns("order_id").build())
                     .start(job)) {
            assertEquals(2, run.queryNames().size());
            source.publishUtf8("order-1", "order-1");
            run.processAllAvailable();
            run.assertHealthy();
            assertFalse(run.kafkaOutputRecords("target").isEmpty());
            assertFalse(run.context().modelWrites("model").isEmpty());
            assertEquals(0, run.context().affectedRows());
            run.stop();
            assertTrue(run.isStopped());
        }
        assertEquals(1, stops.get());
    }
}
