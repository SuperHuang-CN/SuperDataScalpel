package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
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
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKeyType;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;
import cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping;
import cn.superhuang.data.scalpel.contract.task.ShapefilePackageMode;
import cn.superhuang.data.scalpel.contract.task.ShapefileShapeType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlyQueryFingerprint;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
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
                        Set.of(DataSourcePurpose.DISTRIBUTION),
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
    void compilesJdbcQueryInputFromSavedSchemaWithoutAccessingTheDatabase() throws Exception {
        UUID queryDataSourceId = UUID.randomUUID();
        UUID outputDataSourceId = UUID.randomUUID();
        String queryNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        String sql = "SELECT order_id, amount FROM orders";
        String hash = ReadOnlyQueryFingerprint.sha256(ReadOnlySelectQueryParser.parse(sql));
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "order_id", PlatformDataType.LONG, null, null, null,
                        false, null, false, false, "订单 ID"),
                new CanvasColumnSchema(
                        "amount", PlatformDataType.DECIMAL, null, 12, 2,
                        true, null, false, false, "订单金额")
        );
        JdbcQueryInputNodeDefinition queryInput = new JdbcQueryInputNodeDefinition(
                queryNodeId,
                "订单查询输入",
                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                        0.0, 0.0, 240.0, 120.0),
                new JdbcQueryInputConfiguration(
                        queryDataSourceId.toString(), sql, "query_orders", hash, columns)
        );
        JdbcOutputNodeDefinition output = new JdbcOutputNodeDefinition(
                outputNodeId,
                "订单输出",
                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                        320.0, 0.0, 240.0, 120.0),
                new JdbcOutputConfiguration(
                        "query_orders", outputDataSourceId.toString(), "orders_target",
                        JdbcWriteMode.APPEND, ColumnMappingMode.BY_NAME, List.of())
        );
        CanvasDefinition definition = new CanvasDefinition(
                1, 24, List.of(queryInput, output),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(), queryNodeId, outputNodeId))
        );
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        queryDataSourceId, true, ConnectionKind.JDBC,
                        CanvasJdbcDatabaseType.POSTGRESQL,
                        Set.of(DataSourcePurpose.SOURCE), List.of()),
                new MetadataDataSource(
                        outputDataSourceId, true, ConnectionKind.JDBC,
                        CanvasJdbcDatabaseType.POSTGRESQL,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        List.of(new MetadataTable(
                                "orders_target", DatabaseObjectType.TABLE, columns)))
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
        assertEquals(0, jobsStarted.get(), "query schema compilation must not access JDBC or start a job");
        CanvasTableSchema queryTable = compilation.nodeResults().getFirst().outputTables().getFirst();
        assertEquals("query_orders", queryTable.name());
        assertEquals("JDBC_QUERY", queryTable.origin().kind());
        assertEquals(CanvasDatasetKind.BOUNDED, queryTable.datasetKind());
        assertEquals(columns, queryTable.columns());

        JdbcQueryInputNodeDefinition staleInput = new JdbcQueryInputNodeDefinition(
                queryInput.id(), queryInput.name(), queryInput.layout(),
                new JdbcQueryInputConfiguration(
                        queryDataSourceId.toString(), sql, "query_orders", "0".repeat(64), columns)
        );
        CanvasCompilation stale = compiler.compile(
                new CanvasDefinition(1, 24, List.of(staleInput, output), definition.edges()),
                MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());
        assertFalse(stale.valid());
        assertIssue(stale.nodeResults().getFirst(), "JDBC_QUERY_SCHEMA_STALE");
    }

    @Test
    void validatesJdbcOutputUpsertKeysAndWarnsAboutMySqlConflictScope() {
        UUID dataSourceId = UUID.randomUUID();
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "order_id", PlatformDataType.LONG, null, null, null,
                        false, null, false, false, "订单 ID"),
                new CanvasColumnSchema(
                        "order_no", PlatformDataType.STRING, 64, null, null,
                        false, null, false, false, "订单号"),
                new CanvasColumnSchema(
                        "amount", PlatformDataType.DECIMAL, null, 12, 2,
                        true, null, false, false, "订单金额")
        );
        JdbcInputNodeDefinition input = new JdbcInputNodeDefinition(
                inputNodeId,
                "订单暂存输入",
                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                        0.0, 0.0, 240.0, 120.0),
                new JdbcInputConfiguration(dataSourceId.toString(), "orders_stage")
        );
        JdbcOutputNodeDefinition output = new JdbcOutputNodeDefinition(
                outputNodeId,
                "订单 UPSERT",
                new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                        320.0, 0.0, 240.0, 120.0),
                new JdbcOutputConfiguration(
                        "orders_stage", dataSourceId.toString(), "orders",
                        JdbcWriteMode.UPSERT, ColumnMappingMode.BY_NAME, List.of(),
                        List.of("order_id"))
        );
        CanvasDefinition definition = new CanvasDefinition(
                1, 24, List.of(input, output),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(), inputNodeId, outputNodeId))
        );
        MetadataTable source = new MetadataTable(
                "orders_stage", DatabaseObjectType.TABLE, columns);
        MetadataTable target = new MetadataTable(
                "orders", DatabaseObjectType.TABLE, columns,
                List.of(new MetadataUniqueKey(
                        "pk_orders", MetadataUniqueKeyType.PRIMARY_KEY, List.of("order_id"))));
        MetadataSnapshot postgresMetadata = new MetadataSnapshot(List.of(new MetadataDataSource(
                dataSourceId, true, ConnectionKind.JDBC, CanvasJdbcDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                List.of(source, target))), List.of());

        CanvasCompilation postgres = compiler.compile(
                definition, MetadataIndex.create(postgresMetadata),
                sparkSession.newSession(), new AtomicBoolean());
        assertTrue(postgres.valid(), () -> "Compilation issues: " + postgres.nodeResults());

        JdbcOutputNodeDefinition invalidOutput = new JdbcOutputNodeDefinition(
                output.id(), output.name(), output.layout(),
                new JdbcOutputConfiguration(
                        "orders_stage", dataSourceId.toString(), "orders",
                        JdbcWriteMode.UPSERT, ColumnMappingMode.BY_NAME, List.of(),
                        List.of("order_no"))
        );
        CanvasCompilation invalid = compiler.compile(
                new CanvasDefinition(1, 24, List.of(input, invalidOutput), definition.edges()),
                MetadataIndex.create(postgresMetadata), sparkSession.newSession(), new AtomicBoolean());
        assertFalse(invalid.valid());
        assertIssue(invalid.nodeResults().getLast(), "UPSERT_KEY_NOT_UNIQUE_CONSTRAINT");

        MetadataTable mysqlTarget = new MetadataTable(
                "orders", DatabaseObjectType.TABLE, columns,
                List.of(
                        new MetadataUniqueKey(
                                "PRIMARY", MetadataUniqueKeyType.PRIMARY_KEY, List.of("order_id")),
                        new MetadataUniqueKey(
                                "uk_order_no", MetadataUniqueKeyType.UNIQUE_INDEX, List.of("order_no"))
                ));
        MetadataSnapshot mysqlMetadata = new MetadataSnapshot(List.of(new MetadataDataSource(
                dataSourceId, true, ConnectionKind.JDBC, CanvasJdbcDatabaseType.MYSQL,
                Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                List.of(source, mysqlTarget))), List.of());
        CanvasCompilation mysql = compiler.compile(
                definition, MetadataIndex.create(mysqlMetadata),
                sparkSession.newSession(), new AtomicBoolean());
        assertTrue(mysql.valid(), () -> "Compilation issues: " + mysql.nodeResults());
        assertIssue(mysql.nodeResults().getLast(), "MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS");
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
                                        kafkaDataSourceId.toString(),
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
        assertIssue(output, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED");
        assertEquals(List.of("order_events"),
                output.inputTables().stream().map(CanvasTableSchema::name).toList());
    }

    @Test
    void rejectsStorageOnlyDataSourcesForJdbcOutput() throws Exception {
        String storageOnlyJson = Files.readString(examplePath())
                .replace("\"purposes\": [\"DISTRIBUTION\"]", "\"purposes\": [\"STORAGE\"]");
        TaskCompilationRequest request = objectMapper.readValue(storageOnlyJson, TaskCompilationRequest.class);

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
    void compilesBatchFileOutputForAnEnabledDistributionS3WithoutStartingAJob() throws Exception {
        UUID inputDataSourceId = UUID.randomUUID();
        UUID outputDataSourceId = UUID.randomUUID();
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        List<CanvasColumnSchema> columns = List.of(new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null,
                false, null, false, false, "订单 ID"));
        CanvasDefinition definition = new CanvasDefinition(1, 6, List.of(
                new JdbcInputNodeDefinition(
                        inputNodeId,
                        "订单输入",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                0.0, 0.0, 240.0, 120.0),
                        new JdbcInputConfiguration(inputDataSourceId.toString(), "orders")
                ),
                new FileOutputNodeDefinition(
                        outputNodeId,
                        "订单文件输出",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                320.0, 0.0, 240.0, 120.0),
                        new FileOutputConfiguration(
                                "orders",
                                outputDataSourceId.toString(),
                                "exports/orders",
                                FileOutputConflictPolicy.FAIL_IF_EXISTS,
                                new FileOutputFormatOptions.Csv(true, ",", "\"", "\\", "")
                        )
                )
        ), List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputNodeId, outputNodeId)));
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        inputDataSourceId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable("orders", DatabaseObjectType.TABLE, columns))),
                new MetadataDataSource(
                        outputDataSourceId, true, ConnectionKind.S3,
                        Set.of(DataSourcePurpose.DISTRIBUTION), List.of())
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

        assertTrue(compilation.valid());
        assertEquals(2, compilation.nodeResults().size());
        assertTrue(compilation.nodeResults().get(1).outputTables().isEmpty());
        assertEquals(0, jobsStarted.get(), "FILE_OUTPUT compilation must not access S3 or execute Spark");
    }

    @Test
    void compilesStrictShapefileSchemaAndRejectsIncompatibleShapeKind() throws Exception {
        UUID inputDataSourceId = UUID.randomUUID();
        UUID outputDataSourceId = UUID.randomUUID();
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "district_id", PlatformDataType.LONG, null, null, null,
                        false, null, false, false, "行政区 ID"),
                new CanvasColumnSchema(
                        "district_name", PlatformDataType.STRING, 40, null, null,
                        false, null, false, false, "行政区名称"),
                new CanvasColumnSchema(
                        "geom", PlatformDataType.GEOMETRY, null, null, null,
                        true, null, false, false, "边界",
                        new GeometryTypeDefinition(
                                GeometryKind.POLYGON,
                                CrsReference.epsg(4326),
                                CoordinateDimension.XY))
        );
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        inputDataSourceId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable("districts", DatabaseObjectType.TABLE, columns))),
                new MetadataDataSource(
                        outputDataSourceId, true, ConnectionKind.S3,
                        Set.of(DataSourcePurpose.DISTRIBUTION), List.of())
        ), List.of());
        FileOutputFormatOptions.Shapefile validOptions = new FileOutputFormatOptions.Shapefile(
                "districts",
                ShapefilePackageMode.ZIP,
                "geom",
                ShapefileShapeType.POLYGON,
                List.of(
                        new ShapefileAttributeMapping("district_id", "DIST_ID", null),
                        new ShapefileAttributeMapping("district_name", "DIST_NAME", 160)
                )
        );
        CanvasDefinition validDefinition = shapefileDefinition(
                inputDataSourceId, outputDataSourceId, inputNodeId, outputNodeId, validOptions);

        CanvasCompilation valid = compiler.compile(
                validDefinition,
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean());
        assertTrue(valid.valid(), () -> valid.nodeResults().toString());

        FileOutputFormatOptions.Shapefile invalidOptions = new FileOutputFormatOptions.Shapefile(
                "districts",
                ShapefilePackageMode.COMPONENT_DIRECTORY,
                "geom",
                ShapefileShapeType.POLYLINE,
                validOptions.attributeMappings()
        );
        CanvasCompilation invalid = compiler.compile(
                shapefileDefinition(
                        inputDataSourceId, outputDataSourceId,
                        inputNodeId, outputNodeId, invalidOptions),
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean());

        assertFalse(invalid.valid());
        assertIssue(invalid.nodeResults().get(1), "SHAPEFILE_GEOMETRY_KIND_INCOMPATIBLE");
    }

    @Test
    void compilesGeoParquetAndGeoJsonWithoutExecutingSparkAndRejectsInvalidGeoJsonId() throws Exception {
        UUID inputDataSourceId = UUID.randomUUID();
        UUID outputDataSourceId = UUID.randomUUID();
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "district_id", PlatformDataType.LONG, null, null, null,
                        false, null, false, false, "行政区 ID"),
                new CanvasColumnSchema(
                        "amount", PlatformDataType.DECIMAL, null, 12, 2,
                        true, null, false, false, "金额"),
                new CanvasColumnSchema(
                        "geom", PlatformDataType.GEOMETRY, null, null, null,
                        true, null, false, false, "边界",
                        new GeometryTypeDefinition(
                                GeometryKind.POLYGON,
                                CrsReference.epsg(4326),
                                CoordinateDimension.XY))
        );
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        inputDataSourceId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable("districts", DatabaseObjectType.TABLE, columns))),
                new MetadataDataSource(
                        outputDataSourceId, true, ConnectionKind.S3,
                        Set.of(DataSourcePurpose.DISTRIBUTION), List.of())
        ), List.of());
        AtomicInteger jobsStarted = new AtomicInteger();
        sparkSession.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasCompilation geoParquet = compiler.compile(
                spatialFileDefinition(
                        inputDataSourceId,
                        outputDataSourceId,
                        new FileOutputFormatOptions.GeoParquet(
                                "geom",
                                GeoParquetCompressionCodec.ZSTD,
                                GeoParquetCoveringMode.ROW_BBOX)),
                MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());
        CanvasCompilation geoJson = compiler.compile(
                spatialFileDefinition(
                        inputDataSourceId,
                        outputDataSourceId,
                        new FileOutputFormatOptions.GeoJson(
                                "districts", "geom", "district_id", false)),
                MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());
        CanvasCompilation invalidId = compiler.compile(
                spatialFileDefinition(
                        inputDataSourceId,
                        outputDataSourceId,
                        new FileOutputFormatOptions.GeoJson(
                                "districts", "geom", "amount", false)),
                MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());
        sparkSession.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertTrue(geoParquet.valid(), () -> geoParquet.nodeResults().toString());
        assertTrue(geoJson.valid(), () -> geoJson.nodeResults().toString());
        assertFalse(invalidId.valid());
        assertIssue(invalidId.nodeResults().get(1), "GEOJSON_ID_FIELD_INVALID");
        assertEquals(List.of("districts"), invalidId.nodeResults().get(1).inputTables().stream()
                .map(CanvasTableSchema::name).toList());
        assertEquals(0, jobsStarted.get(), "spatial FILE_OUTPUT compilation must stay schema-only");
    }

    @Test
    void rejectsInvalidSpatialFileSchemasAndUnavailableTargetsWithoutHidingInputTables() {
        UUID inputDataSourceId = UUID.randomUUID();
        UUID outputDataSourceId = UUID.randomUUID();
        CanvasColumnSchema identifier = new CanvasColumnSchema(
                "district_id", PlatformDataType.LONG, null, null, null,
                false, null, false, false, "行政区 ID");
        CanvasColumnSchema geometry = geometryColumn(
                "geom", GeometryKind.POLYGON, CrsReference.epsg(4326), CoordinateDimension.XY);

        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometry),
                new FileOutputFormatOptions.GeoParquet(
                        "missing_geom", GeoParquetCompressionCodec.SNAPPY,
                        GeoParquetCoveringMode.ROW_BBOX),
                validSpatialOutput(outputDataSourceId),
                "GEOPARQUET_GEOMETRY_REQUIRED");
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometry, geometryColumn(
                        "centroid", GeometryKind.POINT, CrsReference.epsg(4326),
                        CoordinateDimension.XY)),
                new FileOutputFormatOptions.GeoParquet(
                        "geom", GeoParquetCompressionCodec.SNAPPY,
                        GeoParquetCoveringMode.ROW_BBOX),
                validSpatialOutput(outputDataSourceId),
                "GEOPARQUET_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED");
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometry, new CanvasColumnSchema(
                        "geom_bbox", PlatformDataType.STRING, 128, null, null,
                        true, null, false, false, null)),
                new FileOutputFormatOptions.GeoParquet(
                        "geom", GeoParquetCompressionCodec.SNAPPY,
                        GeoParquetCoveringMode.ROW_BBOX),
                validSpatialOutput(outputDataSourceId),
                "GEOPARQUET_COVERING_COLUMN_CONFLICT");
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometryColumn(
                        "geom", GeometryKind.POLYGON, CrsReference.epsg(999999),
                        CoordinateDimension.XY)),
                new FileOutputFormatOptions.GeoParquet(
                        "geom", GeoParquetCompressionCodec.SNAPPY,
                        GeoParquetCoveringMode.NONE),
                validSpatialOutput(outputDataSourceId),
                "GEOPARQUET_CRS_UNSUPPORTED");
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometryColumn(
                        "geom", GeometryKind.POLYGON, CrsReference.epsg(4490),
                        CoordinateDimension.XY)),
                new FileOutputFormatOptions.GeoJson(
                        "districts", "geom", null, false),
                validSpatialOutput(outputDataSourceId),
                "GEOJSON_CRS_REQUIRES_WGS84");
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, new CanvasColumnSchema(
                                "payload", PlatformDataType.BINARY, null, null, null,
                                true, null, false, false, null), geometry),
                new FileOutputFormatOptions.GeoJson(
                        "districts", "geom", null, false),
                validSpatialOutput(outputDataSourceId),
                "GEOJSON_PROPERTY_TYPE_UNSUPPORTED");

        MetadataDataSource jdbcTarget = new MetadataDataSource(
                outputDataSourceId, true, ConnectionKind.JDBC,
                Set.of(DataSourcePurpose.DISTRIBUTION), List.of());
        assertSpatialFileIssue(
                inputDataSourceId, outputDataSourceId,
                List.of(identifier, geometry),
                new FileOutputFormatOptions.GeoJson(
                        "districts", "geom", null, false),
                jdbcTarget,
                "DATA_SOURCE_UNAVAILABLE");
    }

    private void assertSpatialFileIssue(
            UUID inputDataSourceId,
            UUID outputDataSourceId,
            List<CanvasColumnSchema> columns,
            FileOutputFormatOptions options,
            MetadataDataSource outputDataSource,
            String expectedIssue
    ) {
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        inputDataSourceId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable(
                                "districts", DatabaseObjectType.TABLE, columns))),
                outputDataSource
        ), List.of());
        CanvasCompilation compilation = compiler.compile(
                spatialFileDefinition(inputDataSourceId, outputDataSourceId, options),
                MetadataIndex.create(metadata), sparkSession.newSession(), new AtomicBoolean());

        assertFalse(compilation.valid());
        NodeCompilationResult output = compilation.nodeResults().get(1);
        assertIssue(output, expectedIssue);
        assertEquals(List.of("districts"), output.inputTables().stream()
                .map(CanvasTableSchema::name).toList());
    }

    private static MetadataDataSource validSpatialOutput(UUID outputDataSourceId) {
        return new MetadataDataSource(
                outputDataSourceId, true, ConnectionKind.S3,
                Set.of(DataSourcePurpose.DISTRIBUTION), List.of());
    }

    private static CanvasColumnSchema geometryColumn(
            String name,
            GeometryKind kind,
            CrsReference crs,
            CoordinateDimension dimension
    ) {
        return new CanvasColumnSchema(
                name, PlatformDataType.GEOMETRY, null, null, null,
                true, null, false, false, null,
                new GeometryTypeDefinition(kind, crs, dimension));
    }

    private static CanvasDefinition spatialFileDefinition(
            UUID inputDataSourceId,
            UUID outputDataSourceId,
            FileOutputFormatOptions options
    ) {
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        return new CanvasDefinition(1, 25, List.of(
                new JdbcInputNodeDefinition(
                        inputNodeId,
                        "行政区输入",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                0.0, 0.0, 240.0, 120.0),
                        new JdbcInputConfiguration(inputDataSourceId.toString(), "districts")
                ),
                new FileOutputNodeDefinition(
                        outputNodeId,
                        "空间文件输出",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                320.0, 0.0, 240.0, 120.0),
                        new FileOutputConfiguration(
                                "districts",
                                outputDataSourceId.toString(),
                                "exports/districts",
                                FileOutputConflictPolicy.FAIL_IF_EXISTS,
                                options
                        )
                )
        ), List.of(new CanvasEdgeDefinition(
                UUID.randomUUID().toString(), inputNodeId, outputNodeId)));
    }

    private static CanvasDefinition shapefileDefinition(
            UUID inputDataSourceId,
            UUID outputDataSourceId,
            String inputNodeId,
            String outputNodeId,
            FileOutputFormatOptions.Shapefile options
    ) {
        return new CanvasDefinition(1, 24, List.of(
                new JdbcInputNodeDefinition(
                        inputNodeId,
                        "行政区输入",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                0.0, 0.0, 240.0, 120.0),
                        new JdbcInputConfiguration(inputDataSourceId.toString(), "districts")
                ),
                new FileOutputNodeDefinition(
                        outputNodeId,
                        "Shapefile 输出",
                        new cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout(
                                320.0, 0.0, 240.0, 120.0),
                        new FileOutputConfiguration(
                                "districts",
                                outputDataSourceId.toString(),
                                "exports/districts",
                                FileOutputConflictPolicy.FAIL_IF_EXISTS,
                                options
                        )
                )
        ), List.of(new CanvasEdgeDefinition(
                UUID.randomUUID().toString(), inputNodeId, outputNodeId)));
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
