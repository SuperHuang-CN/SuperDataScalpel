package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CanvasTaskCompilerSparkTest {
    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();
    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private SparkSession sparkSession;

    @BeforeAll
    void startSpark() {
        sparkSession = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-task-compiler-test")
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
        if (sparkSession != null) sparkSession.stop();
    }

    @Test
    void compilesTheCompleteExampleWithoutStartingASparkJob() throws Exception {
        TaskCompilationRequest request = example();
        AtomicInteger jobsStarted = new AtomicInteger();
        sparkSession.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasCompilation compilation = compile(request.task().definition(), request);
        sparkSession.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertTrue(compilation.valid());
        assertEquals(4, compilation.nodeResults().size());
        assertEquals(0, jobsStarted.get(), "schema compilation must not execute a Spark action");

        NodeCompilationResult join = compilation.nodeResults().get(2);
        CanvasTableSchema joined = join.outputTables().stream()
                .filter(table -> table.name().equals("order_customer"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("order_id", "customer_id", "customer_key", "customer_name"),
                joined.columns().stream().map(CanvasColumnSchema::name).toList());
        assertFalse(column(joined, "order_id").nullable());
        assertTrue(column(joined, "customer_key").nullable());
        assertEquals("客户名称", column(joined, "customer_name").comment());
        assertEquals(100, column(joined, "customer_name").length());
        assertEquals("OK", compilation.nodeResults().get(3).state().name());
    }

    @Test
    void compilesHttpApiInputFromDeclaredResourceSchemaWithoutRemoteRequestsOrSparkJobs() throws Exception {
        UUID apiDataSourceId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID storageDataSourceId = UUID.randomUUID();
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "id", PlatformDataType.INTEGER, null, null, null,
                        false, null, false, false, "订单 ID"),
                new CanvasColumnSchema(
                        "name", PlatformDataType.STRING, 100, null, null,
                        true, null, false, false, "订单名称")
        );
        CanvasDefinition definition = new CanvasDefinition(1, 1, List.of(
                new HttpApiInputNodeDefinition(
                        inputNodeId,
                        "HTTP API 订单输入",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                0.0, 0.0, 240.0, 120.0),
                        new HttpApiInputConfiguration(
                                apiDataSourceId.toString(), resourceId.toString(), "api_orders", List.of())
                ),
                new JdbcOutputNodeDefinition(
                        outputNodeId,
                        "订单输出",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                320.0, 0.0, 240.0, 120.0),
                        new JdbcOutputConfiguration(
                                "api_orders", storageDataSourceId.toString(), "orders_target",
                                JdbcWriteMode.APPEND, ColumnMappingMode.BY_NAME, List.of())
                )
        ), List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputNodeId, outputNodeId)));
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        apiDataSourceId, true, ConnectionKind.HTTP_API,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable(
                                resourceId.toString(), DatabaseObjectType.API_RESOURCE, columns))),
                new MetadataDataSource(
                        storageDataSourceId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.STORAGE),
                        List.of(new MetadataTable("orders_target", DatabaseObjectType.TABLE, columns)))
        ), List.of());
        AtomicInteger jobsStarted = new AtomicInteger();
        sparkSession.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasCompilation compilation = compiler.compile(
                definition, MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());
        sparkSession.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertTrue(compilation.valid(), () -> "Compilation issues: " + compilation.nodeResults());
        assertEquals(0, jobsStarted.get(), "HTTP API schema compilation must not execute a remote call or Spark job");
        CanvasTableSchema output = compilation.nodeResults().getFirst().outputTables().getFirst();
        assertEquals("api_orders", output.name());
        assertEquals("HTTP_API", output.origin().kind());
        assertEquals(apiDataSourceId, output.origin().dataSourceId());
        assertEquals(resourceId.toString(), output.origin().tableName());
        assertEquals(columns, output.columns());
        assertEquals("OK", compilation.nodeResults().getLast().state().name());
    }

    @Test
    void exposesKafkaUpstreamTableToAnInvalidStreamingOutput() {
        UUID kafkaDataSourceId = UUID.randomUUID();
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        CanvasDefinition definition = new CanvasDefinition(
                1,
                5,
                List.of(
                        new KafkaInputNodeDefinition(
                                inputNodeId,
                                "Kafka 输入",
                                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                        0.0, 0.0, 240.0, 120.0),
                                new KafkaInputConfiguration(
                                        kafkaDataSourceId,
                                        "order-events",
                                        new KafkaValueSchema(List.of(new KafkaValueColumn(
                                                "order_id",
                                                PlatformDataType.LONG,
                                                null,
                                                null,
                                                null,
                                                false,
                                                "订单 ID"
                                        ))),
                                        "order_events",
                                        KafkaStartingOffsets.LATEST
                                )
                        ),
                        new JdbcOutputNodeDefinition(
                                outputNodeId,
                                "JDBC 输出",
                                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                        320.0, 0.0, 240.0, 120.0),
                                new JdbcOutputConfiguration(
                                        "",
                                        "",
                                        "",
                                        JdbcWriteMode.OVERWRITE,
                                        null,
                                        List.of()
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(),
                        inputNodeId,
                        outputNodeId
                ))
        );
        MetadataSnapshot metadata = new MetadataSnapshot(
                List.of(new MetadataDataSource(
                        kafkaDataSourceId,
                        true,
                        ConnectionKind.KAFKA,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of()
                )),
                List.of()
        );

        CanvasCompilation compilation = compiler.compile(
                definition,
                CanvasExecutionMode.STREAMING,
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean()
        );

        assertFalse(compilation.valid());
        assertEquals("OK", compilation.nodeResults().getFirst().state().name());
        NodeCompilationResult output = compilation.nodeResults().getLast();
        assertIssue(output, "STREAMING_JDBC_OUTPUT_REQUIRES_APPEND");
        assertEquals(List.of("order_events"),
                output.inputTables().stream().map(CanvasTableSchema::name).toList());
    }

    @Test
    void rejectsDistributionOnlyDataSourcesForJdbcOutput() throws Exception {
        String distributionOnlyJson = Files.readString(examplePath())
                .replace("\"purposes\": [\"STORAGE\"]", "\"purposes\": [\"DISTRIBUTION\"]");
        TaskCompilationRequest request = objectMapper.readValue(distributionOnlyJson, TaskCompilationRequest.class);

        CanvasCompilation compilation = compile(request.task().definition(), request);

        assertFalse(compilation.valid());
        assertIssue(compilation.nodeResults().get(3), "DATA_SOURCE_UNAVAILABLE");
    }

    @Test
    void reportsDuplicateColumnsAndDuplicateUpstreamTableNames() throws Exception {
        TaskCompilationRequest request = example();
        String duplicateColumnJson = Files.readString(examplePath())
                .replaceFirst("\"name\": \"customer_key\"", "\"name\": \"customer_id\"")
                .replace("\"rightColumnName\": \"customer_key\"", "\"rightColumnName\": \"customer_id\"");
        TaskCompilationRequest duplicateColumnRequest = objectMapper.readValue(
                duplicateColumnJson, TaskCompilationRequest.class);

        CanvasCompilation duplicateColumns = compile(
                duplicateColumnRequest.task().definition(), duplicateColumnRequest);
        assertFalse(duplicateColumns.valid());
        assertIssue(duplicateColumns.nodeResults().get(2), "DUPLICATE_COLUMN_NAME");

        List<CanvasNodeDefinition> nodes = new ArrayList<>(request.task().definition().nodes());
        JdbcInputNodeDefinition secondInput = (JdbcInputNodeDefinition) nodes.get(1);
        nodes.set(1, new JdbcInputNodeDefinition(
                secondInput.id(),
                secondInput.name(),
                secondInput.layout(),
                new JdbcInputConfiguration(
                        secondInput.configuration().dataSourceId(),
                        "orders"
                )
        ));
        CanvasDefinition definition = new CanvasDefinition(
                1,
                1,
                List.copyOf(nodes),
                request.task().definition().edges()
        );
        CanvasCompilation duplicateTables = compile(definition, request);
        assertFalse(duplicateTables.valid());
        assertIssue(duplicateTables.nodeResults().get(2), "DUPLICATE_TABLE_NAME");
    }

    @Test
    void reportsCyclesAndExplicitOutputMappingErrorsWithoutHidingOtherNodes() throws Exception {
        TaskCompilationRequest request = example();
        List<CanvasEdgeDefinition> edges = new ArrayList<>(request.task().definition().edges());
        edges.add(new CanvasEdgeDefinition(
                UUID.randomUUID().toString(),
                request.task().definition().nodes().get(3).id(),
                request.task().definition().nodes().get(0).id()
        ));
        CanvasCompilation cycle = compile(new CanvasDefinition(
                1,
                1,
                request.task().definition().nodes(),
                edges
        ), request);
        assertFalse(cycle.valid());
        assertTrue(cycle.canvasIssues().stream().anyMatch(issue -> issue.code().equals("CANVAS_CYCLE")));

        List<CanvasNodeDefinition> nodes = new ArrayList<>(request.task().definition().nodes());
        JdbcOutputNodeDefinition output = (JdbcOutputNodeDefinition) nodes.get(3);
        JdbcOutputConfiguration configuration = output.configuration();
        nodes.set(3, new JdbcOutputNodeDefinition(
                output.id(),
                output.name(),
                output.layout(),
                new JdbcOutputConfiguration(
                        configuration.sourceTableName(),
                        configuration.dataSourceId(),
                        configuration.targetTableName(),
                        configuration.writeMode(),
                        cn.superhuang.data.scalpel.contract.task.ColumnMappingMode.EXPLICIT,
                        List.of()
                )
        ));
        CanvasCompilation invalidMapping = compile(new CanvasDefinition(
                1,
                1,
                List.copyOf(nodes),
                request.task().definition().edges()
        ), request);
        assertFalse(invalidMapping.valid());
        assertIssue(invalidMapping.nodeResults().get(3), "REQUIRED_CONFIGURATION");
        assertEquals("OK", invalidMapping.nodeResults().get(2).state().name());
    }

    @Test
    void usesSparkOuterJoinNullabilityForEverySupportedJoinType() throws Exception {
        TaskCompilationRequest request = example();
        for (JoinType joinType : JoinType.values()) {
            List<CanvasNodeDefinition> nodes = new ArrayList<>(request.task().definition().nodes());
            JoinNodeDefinition join = (JoinNodeDefinition) nodes.get(2);
            JoinConfiguration configuration = join.configuration();
            nodes.set(2, new JoinNodeDefinition(
                    join.id(),
                    join.name(),
                    join.layout(),
                    new JoinConfiguration(
                            configuration.leftTableName(),
                            configuration.rightTableName(),
                            configuration.outputTableName(),
                            joinType,
                            configuration.conditions()
                    )
            ));

            CanvasCompilation compilation = compile(new CanvasDefinition(
                    1,
                    1,
                    List.copyOf(nodes),
                    request.task().definition().edges()
            ), request);
            NodeCompilationResult joinResult = compilation.nodeResults().get(2);
            assertEquals("OK", joinResult.state().name(), () -> joinType + " issues: " + joinResult.issues());
            CanvasTableSchema joined = joinResult.outputTables().stream()
                    .filter(table -> table.name().equals("order_customer"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(joinType == JoinType.RIGHT || joinType == JoinType.FULL,
                    column(joined, "order_id").nullable());
            assertEquals(joinType == JoinType.LEFT || joinType == JoinType.FULL,
                    column(joined, "customer_key").nullable());
            if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
                assertTrue(compilation.valid());
                assertEquals("WARNING", compilation.nodeResults().get(3).state().name());
                assertIssue(compilation.nodeResults().get(3), "NULLABILITY_RISK");
            } else {
                assertEquals("OK", compilation.nodeResults().get(3).state().name());
            }
        }
    }

    @Test
    void letsSparkAnalyzeSafeNumericJoinCoercion() throws Exception {
        TaskCompilationRequest request = example();
        MetadataSnapshot metadata = new MetadataSnapshot(
                request.metadataSnapshot().dataSources().stream()
                        .map(dataSource -> new MetadataDataSource(
                                dataSource.id(),
                                dataSource.enabled(),
                                dataSource.connectionKind(),
                                dataSource.purposes(),
                                dataSource.tables().stream()
                                        .map(table -> new MetadataTable(
                                                table.tableName(),
                                                table.objectType(),
                                                table.columns().stream()
                                                        .map(column -> column.name().equals("customer_key")
                                                                ? new CanvasColumnSchema(
                                                                column.name(),
                                                                PlatformDataType.INTEGER,
                                                                column.length(),
                                                                column.precision(),
                                                                column.scale(),
                                                                column.nullable(),
                                                                column.defaultValue(),
                                                                column.autoIncrement(),
                                                                column.generated(),
                                                                column.comment()
                                                        ) : column)
                                                        .toList()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                request.metadataSnapshot().models()
        );

        CanvasCompilation compilation = compiler.compile(
                request.task().definition(),
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean()
        );

        assertTrue(compilation.valid(), () -> "Compilation issues: " + compilation.nodeResults());
        assertEquals("OK", compilation.nodeResults().get(2).state().name());
    }

    @Test
    void letsSparkAnalyzeValueDependentJoinCoercionWithoutPlatformTypeWarnings() throws Exception {
        TaskCompilationRequest request = example();
        MetadataSnapshot metadata = new MetadataSnapshot(
                request.metadataSnapshot().dataSources().stream()
                        .map(dataSource -> new MetadataDataSource(
                                dataSource.id(),
                                dataSource.enabled(),
                                dataSource.connectionKind(),
                                dataSource.purposes(),
                                dataSource.tables().stream()
                                        .map(table -> new MetadataTable(
                                                table.tableName(),
                                                table.objectType(),
                                                table.columns().stream()
                                                        .map(column -> column.name().equals("customer_key")
                                                                ? withType(column, PlatformDataType.STRING)
                                                                : column)
                                                        .toList()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                request.metadataSnapshot().models()
        );

        CanvasCompilation compilation = compiler.compile(
                request.task().definition(),
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean()
        );

        assertTrue(compilation.valid(), () -> "Compilation issues: " + compilation.nodeResults());
        assertEquals("OK", compilation.nodeResults().get(2).state().name());
        assertTrue(compilation.nodeResults().get(2).issues().isEmpty());
    }

    @Test
    void rejectsAJoinComparisonThatSparkCannotAnalyze() throws Exception {
        TaskCompilationRequest request = example();
        MetadataSnapshot metadata = new MetadataSnapshot(
                request.metadataSnapshot().dataSources().stream()
                        .map(dataSource -> new MetadataDataSource(
                                dataSource.id(),
                                dataSource.enabled(),
                                dataSource.connectionKind(),
                                dataSource.purposes(),
                                dataSource.tables().stream()
                                        .map(table -> new MetadataTable(
                                                table.tableName(),
                                                table.objectType(),
                                                table.columns().stream()
                                                        .map(column -> {
                                                            if (column.name().equals("customer_id")) {
                                                                return withType(column, PlatformDataType.BINARY);
                                                            }
                                                            if (column.name().equals("customer_key")) {
                                                                return withType(column, PlatformDataType.DATE);
                                                            }
                                                            return column;
                                                        })
                                                        .toList()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                request.metadataSnapshot().models()
        );

        CanvasCompilation compilation = compiler.compile(
                request.task().definition(),
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean()
        );

        assertFalse(compilation.valid());
        assertIssue(compilation.nodeResults().get(2), "SPARK_ANALYSIS_ERROR");
    }

    @Test
    void shippedConflictExamplesProduceTheirDocumentedIssues() throws Exception {
        TaskCompilationRequest duplicateTable = objectMapper.readValue(
                Files.readString(Path.of("examples/duplicate-table-name-compilation.json")),
                TaskCompilationRequest.class
        );
        TaskCompilationRequest duplicateColumn = objectMapper.readValue(
                Files.readString(Path.of("examples/duplicate-column-name-compilation.json")),
                TaskCompilationRequest.class
        );

        CanvasCompilation tableResult = compile(duplicateTable.task().definition(), duplicateTable);
        CanvasCompilation columnResult = compile(duplicateColumn.task().definition(), duplicateColumn);

        assertFalse(tableResult.valid());
        assertIssue(tableResult.nodeResults().get(2), "DUPLICATE_TABLE_NAME");
        assertFalse(columnResult.valid());
        assertIssue(columnResult.nodeResults().get(2), "DUPLICATE_COLUMN_NAME");
    }

    private CanvasCompilation compile(CanvasDefinition definition, TaskCompilationRequest request) {
        return compiler.compile(
                definition,
                MetadataIndex.create(request.metadataSnapshot()),
                sparkSession.newSession(),
                new AtomicBoolean()
        );
    }

    private TaskCompilationRequest example() throws Exception {
        return objectMapper.readValue(Files.readString(examplePath()), TaskCompilationRequest.class);
    }

    private static Path examplePath() {
        return Path.of("examples/valid-canvas-compilation.json");
    }

    private static CanvasColumnSchema column(CanvasTableSchema table, String name) {
        return table.columns().stream().filter(column -> column.name().equals(name)).findFirst().orElseThrow();
    }

    private static CanvasColumnSchema withType(
            CanvasColumnSchema column,
            PlatformDataType fieldType
    ) {
        return new CanvasColumnSchema(
                column.name(),
                fieldType,
                fieldType == PlatformDataType.STRING ? column.length() : null,
                fieldType == PlatformDataType.DECIMAL ? column.precision() : null,
                fieldType == PlatformDataType.DECIMAL ? column.scale() : null,
                column.nullable(),
                column.defaultValue(),
                column.autoIncrement(),
                column.generated(),
                column.comment()
        );
    }

    private static void assertIssue(NodeCompilationResult result, String code) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals(code)),
                () -> "Expected issue " + code + " but got " + result.issues());
    }
}
