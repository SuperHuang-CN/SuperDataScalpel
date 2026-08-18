package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKeyType;
import cn.superhuang.data.scalpel.contract.task.SnapshotDeletePolicy;
import cn.superhuang.data.scalpel.contract.task.SnapshotTargetOnlyAction;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SnapshotSyncOperatorSupportSparkTest {
    private final SnapshotSyncOperatorSupport support = new SnapshotSyncOperatorSupport();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("snapshot-sync-operator-support-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void returnsTheExplicitlyCastDatasetUsedByRuntimeComparison() {
        CanvasTableSchema sourceSchema = table(
                "reservoir_snapshot",
                CanvasDatasetKind.BOUNDED,
                List.of(column("reservoir_id", PlatformDataType.STRING, false, false, false))
        );
        Dataset<Row> sourceDataset = spark.createDataFrame(
                List.of(RowFactory.create("1")),
                SparkTypeMapper.toStructType(sourceSchema.columns())
        );
        CanvasTableSchema targetSchema = table(
                "reservoir",
                CanvasDatasetKind.BOUNDED,
                List.of(column("reservoir_id", PlatformDataType.INTEGER, false, false, false))
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = support.validateAndMap(
                configuration(
                        sourceSchema.name(),
                        List.of("reservoir_id"),
                        new SnapshotDeletePolicy(SnapshotTargetOnlyAction.KEEP, null, null)
                ),
                Map.of(sourceSchema.name(), new SparkCanvasTable(sourceSchema, sourceDataset)),
                targetSchema,
                List.of(new MetadataUniqueKey(
                        "pk_reservoir", MetadataUniqueKeyType.PRIMARY_KEY, List.of("reservoir_id"))),
                issues
        );

        assertFalse(issues.hasErrors());
        assertTrue(issues.codes.contains("COLUMN_CAST_RISK"));
        assertFalse(issues.codes.contains("SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE"));
        assertEquals(DataTypes.IntegerType, selected.schema().apply("reservoir_id").dataType());
        assertEquals(1, selected.first().getInt(0));
    }

    @Test
    void rejectsUnboundedInputBeforePreparingAnOutput() {
        CanvasTableSchema sourceSchema = table(
                "events",
                CanvasDatasetKind.UNBOUNDED,
                List.of(column("id", PlatformDataType.LONG, false, false, false))
        );
        CanvasTableSchema targetSchema = table(
                "entity",
                CanvasDatasetKind.BOUNDED,
                sourceSchema.columns()
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = support.validateAndMap(
                configuration(sourceSchema.name(), List.of("id"), keep()),
                Map.of(sourceSchema.name(), new SparkCanvasTable(
                        sourceSchema,
                        spark.createDataFrame(List.<Row>of(), SparkTypeMapper.toStructType(sourceSchema.columns()))
                )),
                targetSchema,
                List.of(),
                issues
        );

        assertNull(selected);
        assertTrue(issues.hasErrors());
        assertTrue(issues.codes.contains("SNAPSHOT_SYNC_REQUIRES_BOUNDED_INPUT"));
    }

    @Test
    void validatesDeleteThresholdsInSharedSupport() {
        CanvasTableSchema sourceSchema = table(
                "source",
                CanvasDatasetKind.BOUNDED,
                List.of(column("id", PlatformDataType.LONG, true, false, false))
        );
        CanvasTableSchema targetSchema = table(
                "target",
                CanvasDatasetKind.BOUNDED,
                List.of(column("id", PlatformDataType.LONG, true, false, false))
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = support.validateAndMap(
                configuration(
                        sourceSchema.name(),
                        List.of("id"),
                        new SnapshotDeletePolicy(SnapshotTargetOnlyAction.DELETE, 0L, 1.1D)
                ),
                Map.of(sourceSchema.name(), new SparkCanvasTable(
                        sourceSchema,
                        spark.createDataFrame(List.<Row>of(), SparkTypeMapper.toStructType(sourceSchema.columns()))
                )),
                targetSchema,
                List.of(),
                issues
        );

        assertNull(selected);
        assertTrue(issues.hasErrors());
        assertEquals(2L, issues.codes.stream()
                .filter("SNAPSHOT_SYNC_DELETE_POLICY_INVALID"::equals)
                .count());
    }

    @Test
    void reportsNullableAndNonDatabaseUniqueKeysWithoutBlockingThePlan() {
        CanvasTableSchema sourceSchema = table(
                "source",
                CanvasDatasetKind.BOUNDED,
                List.of(column("business_code", PlatformDataType.STRING, true, false, false))
        );
        CanvasTableSchema targetSchema = table(
                "target",
                CanvasDatasetKind.BOUNDED,
                sourceSchema.columns()
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = support.validateAndMap(
                configuration(sourceSchema.name(), List.of("business_code"), keep()),
                Map.of(sourceSchema.name(), new SparkCanvasTable(
                        sourceSchema,
                        spark.createDataFrame(List.<Row>of(), SparkTypeMapper.toStructType(sourceSchema.columns()))
                )),
                targetSchema,
                List.of(),
                issues
        );

        assertFalse(issues.hasErrors());
        assertEquals(List.of(
                "SNAPSHOT_SYNC_NULLABLE_KEY",
                "SNAPSHOT_SYNC_KEY_NOT_DATABASE_UNIQUE"
        ), issues.codes);
        assertEquals(List.of("business_code"), List.of(selected.columns()));
    }

    private static JdbcSnapshotSyncOutputConfiguration configuration(
            String sourceTableName,
            List<String> keys,
            SnapshotDeletePolicy policy
    ) {
        return new JdbcSnapshotSyncOutputConfiguration(
                sourceTableName,
                "6f8ae8d5-8594-4d3d-9eb4-da90c1ce84cb",
                "reservoir",
                keys,
                keys.stream().map(key -> new JdbcColumnMapping(key, key)).toList(),
                policy
        );
    }

    private static SnapshotDeletePolicy keep() {
        return new SnapshotDeletePolicy(SnapshotTargetOnlyAction.KEEP, null, null);
    }

    private static CanvasTableSchema table(
            String name,
            CanvasDatasetKind kind,
            List<CanvasColumnSchema> columns
    ) {
        return new CanvasTableSchema(name, null, columns, kind, null, null);
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable,
            boolean autoIncrement,
            boolean generated
    ) {
        return new CanvasColumnSchema(
                name, type, null, null, null,
                nullable, null, autoIncrement, generated, null
        );
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }
    }
}
