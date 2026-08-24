package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialJdbcRuntimeSupportTest {
    private SparkSession sparkSession;

    @BeforeAll
    void startSpark() {
        sparkSession = SparkSession.builder()
                .master("local[1]")
                .appName("spatial-jdbc-runtime-support-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (sparkSession != null) sparkSession.stop();
    }

    @Test
    void selectsSpatialWriterOnlyWhenProjectedMappingContainsGeometry() {
        CanvasColumnSchema id = new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null,
                false, null, true, false, null
        );
        CanvasColumnSchema location = new CanvasColumnSchema(
                "location",
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                true,
                null,
                false,
                false,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        CrsReference.epsg(4326),
                        CoordinateDimension.XY
                )
        );
        CanvasTableSchema target = new CanvasTableSchema(
                "target",
                null,
                List.of(id, location)
        );

        assertFalse(SpatialJdbcRuntimeSupport.requiresSpatialWriter(
                target,
                SparkTypeMapper.toStructType(List.of(id))
        ));
        assertTrue(SpatialJdbcRuntimeSupport.requiresSpatialWriter(
                target,
                SparkTypeMapper.toStructType(List.of(location))
        ));
    }

    @Test
    void rejectsNullAndDuplicateUpsertKeysWithoutExposingTheirValues() {
        CanvasColumnSchema id = new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null,
                true, null, false, false, null
        );
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(id));

        Dataset<Row> nullKey = sparkSession.createDataFrame(
                List.of(RowFactory.create(new Object[]{null})),
                SparkTypeMapper.toStructType(List.of(id))
        );
        RunnerExecutionException nullException = assertThrows(
                RunnerExecutionException.class,
                () -> SpatialJdbcRuntimeSupport.validateUpsertKeys(
                        preparedOutput(target, nullKey), nullKey)
        );
        assertEquals("UPSERT_KEY_NULL", nullException.code());
        assertFalse(nullException.getMessage().contains("null-key-value"));

        Dataset<Row> duplicateKey = sparkSession.createDataFrame(
                List.of(RowFactory.create(7L), RowFactory.create(7L)),
                SparkTypeMapper.toStructType(List.of(id))
        );
        RunnerExecutionException duplicateException = assertThrows(
                RunnerExecutionException.class,
                () -> SpatialJdbcRuntimeSupport.validateUpsertKeys(
                        preparedOutput(target, duplicateKey), duplicateKey)
        );
        assertEquals("UPSERT_DUPLICATE_KEY", duplicateException.code());
        assertFalse(duplicateException.getMessage().contains("7"));
    }

    private static CanvasPreparedOutput preparedOutput(
            CanvasTableSchema target,
            Dataset<Row> dataset
    ) {
        JdbcOutputNodeDefinition node = new JdbcOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "UPSERT 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new JdbcOutputConfiguration(
                        "source", UUID.randomUUID().toString(), "target",
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("id", "id")), List.of("id"))
        );
        RuntimeDataSource runtime = new RuntimeDataSource(
                UUID.randomUUID(), RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.DISTRIBUTION), null
        );
        return new CanvasPreparedOutput(
                node, UUID.randomUUID().toString(), "source", runtime,
                new TableIdentifier(null, null, "target"),
                "target", "target", JdbcWriteMode.UPSERT,
                dataset, target, Map.of(), List.of("id")
        );
    }
}
