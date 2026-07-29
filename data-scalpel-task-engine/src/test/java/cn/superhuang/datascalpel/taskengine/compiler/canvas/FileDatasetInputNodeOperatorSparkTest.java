package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.contract.task.FileDatasetType;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataFileDatasetTable;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileDatasetInputNodeOperatorSparkTest {
    private static final String TABLE_CODE = "orders_file";
    private static final List<CanvasColumnSchema> COLUMNS = List.of(new CanvasColumnSchema(
            "order_id",
            PlatformDataType.LONG,
            null,
            null,
            null,
            false,
            null,
            false,
            false,
            "订单 ID"
    ));

    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("file-dataset-input-compiler-test")
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
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void compilesReadyFileTableAsBoundedWithoutStartingSparkJob() throws TimeoutException {
        UUID tableId = UUID.randomUUID();
        AtomicInteger jobsStarted = new AtomicInteger();
        spark.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasCompilation compilation = compile(
                definition(4, tableId.toString()),
                metadata(tableId, FileDatasetParseStatus.READY, FileDatasetFileStatus.READY, COLUMNS),
                CanvasExecutionMode.BATCH
        );
        spark.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertTrue(compilation.valid(), () -> "Compilation issues: " + compilation.nodeResults());
        assertEquals(0, jobsStarted.get(), "file input compilation must not trigger an object read or Spark action");
        var table = compilation.nodeResults().getFirst().outputTables().getFirst();
        assertEquals(TABLE_CODE, table.name());
        assertEquals("FILE_DATASET", table.origin().kind());
        assertEquals(tableId, table.origin().fileDatasetTableId());
        assertEquals("BOUNDED", table.datasetKind().name());
    }

    @Test
    void compilesSchemaReadyFileTableBecausePreviewCapabilityDoesNotLimitRuntimeReads() {
        UUID tableId = UUID.randomUUID();

        CanvasCompilation compilation = compile(
                definition(4, tableId.toString()),
                metadata(tableId, FileDatasetParseStatus.SCHEMA_READY, FileDatasetFileStatus.READY, COLUMNS),
                CanvasExecutionMode.BATCH
        );

        assertTrue(compilation.valid(), () -> "Compilation issues: " + compilation.nodeResults());
    }

    @Test
    void rejectsUnavailableFileMetadataAndEmptySchemaWithStableCodes() {
        UUID tableId = UUID.randomUUID();

        assertIssue(compile(
                definition(4, tableId.toString()),
                metadata(tableId, FileDatasetParseStatus.PARSING, FileDatasetFileStatus.READY, COLUMNS),
                CanvasExecutionMode.BATCH
        ), "FILE_DATASET_TABLE_NOT_READY");
        assertIssue(compile(
                definition(4, tableId.toString()),
                metadata(tableId, FileDatasetParseStatus.READY, FileDatasetFileStatus.PREPARING, COLUMNS),
                CanvasExecutionMode.BATCH
        ), "FILE_DATASET_FILE_NOT_READY");
        assertIssue(compile(
                definition(4, tableId.toString()),
                metadata(tableId, FileDatasetParseStatus.READY, FileDatasetFileStatus.READY, List.of()),
                CanvasExecutionMode.BATCH
        ), "FILE_DATASET_SCHEMA_EMPTY");
        assertIssue(compile(
                definition(4, tableId.toString()),
                metadataWithoutFileTable(),
                CanvasExecutionMode.BATCH
        ), "FILE_DATASET_TABLE_NOT_FOUND");
        assertIssue(compile(
                definition(4, "not-a-uuid"),
                metadataWithoutFileTable(),
                CanvasExecutionMode.BATCH
        ), "FILE_DATASET_TABLE_ID_REQUIRED");
    }

    @Test
    void enforcesCanvasOneDotFourAndBatchOnlyCapability() {
        UUID tableId = UUID.randomUUID();
        MetadataSnapshot metadata = metadata(
                tableId, FileDatasetParseStatus.READY, FileDatasetFileStatus.READY, COLUMNS);

        assertIssue(compile(
                definition(3, tableId.toString()),
                metadata,
                CanvasExecutionMode.BATCH
        ), "NODE_TYPE_REQUIRES_SCHEMA_VERSION");
        assertIssue(compile(
                definition(4, tableId.toString()),
                metadata,
                CanvasExecutionMode.STREAMING
        ), "NODE_EXECUTION_MODE_NOT_SUPPORTED");
    }

    private CanvasCompilation compile(
            CanvasDefinition definition,
            MetadataSnapshot metadata,
            CanvasExecutionMode mode
    ) {
        return compiler.compile(
                definition,
                mode,
                MetadataIndex.create(metadata),
                spark.newSession(),
                new AtomicBoolean()
        );
    }

    private static CanvasDefinition definition(int schemaMinorVersion, String tableId) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        UUID targetDataSourceId = targetDataSourceId();
        return new CanvasDefinition(
                1,
                schemaMinorVersion,
                List.of(
                        new FileDatasetInputNodeDefinition(
                                inputId,
                                "订单文件输入",
                                new CanvasNodeLayout(0D, 0D, 240D, 120D),
                                new FileDatasetInputConfiguration(tableId)
                        ),
                        new JdbcOutputNodeDefinition(
                                outputId,
                                "订单输出",
                                new CanvasNodeLayout(320D, 0D, 240D, 120D),
                                new JdbcOutputConfiguration(
                                        TABLE_CODE,
                                        targetDataSourceId.toString(),
                                        "orders_target",
                                        JdbcWriteMode.APPEND,
                                        ColumnMappingMode.BY_NAME,
                                        List.of()
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );
    }

    private static MetadataSnapshot metadata(
            UUID tableId,
            FileDatasetParseStatus parseStatus,
            FileDatasetFileStatus fileStatus,
            List<CanvasColumnSchema> columns
    ) {
        return new MetadataSnapshot(
                List.of(targetDataSource()),
                List.of(),
                List.of(new MetadataFileDatasetTable(
                        tableId,
                        TABLE_CODE,
                        "订单文件表",
                        FileDatasetType.PARQUET,
                        parseStatus,
                        fileStatus,
                        columns
                ))
        );
    }

    private static MetadataSnapshot metadataWithoutFileTable() {
        return new MetadataSnapshot(List.of(targetDataSource()), List.of(), List.of());
    }

    private static MetadataDataSource targetDataSource() {
        return new MetadataDataSource(
                targetDataSourceId(),
                true,
                ConnectionKind.JDBC,
                Set.of(DataSourcePurpose.DISTRIBUTION),
                List.of(new MetadataTable("orders_target", DatabaseObjectType.TABLE, COLUMNS))
        );
    }

    private static UUID targetDataSourceId() {
        return UUID.fromString("ab632e0a-5813-470e-b63e-a966d1dbf71d");
    }

    private static void assertIssue(CanvasCompilation compilation, String code) {
        assertFalse(compilation.valid());
        assertTrue(compilation.nodeResults().stream()
                .flatMap(result -> result.issues().stream())
                .map(issue -> issue.code())
                .anyMatch(code::equals), () -> "Missing " + code + " in " + compilation.nodeResults());
    }
}
