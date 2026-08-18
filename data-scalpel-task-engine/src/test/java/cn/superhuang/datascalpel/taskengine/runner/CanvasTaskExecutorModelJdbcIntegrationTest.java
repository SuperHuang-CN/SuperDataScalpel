package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKeyType;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "datascalpel.task-execution-it", matches = "true")
class CanvasTaskExecutorModelJdbcIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("canvas_model_test")
            .withUsername("canvas")
            .withPassword("canvas-password");

    private static final String MODEL_SCHEMA = "model_scope";
    private static final String SOURCE_TABLE = "source_orders_physical";
    private static final String TARGET_TABLE = "target_orders_physical";
    private static final String SOURCE_CODE = "source_orders";

    private final UUID dataSourceId = UUID.fromString("99930bd8-14d9-493b-a66d-bb66658a39e1");
    private final UUID sourceModelId = UUID.fromString("d9d1cf9c-9c2c-4c42-9180-178a51074696");
    private final UUID targetModelId = UUID.fromString("f4ef3b16-b090-48cb-a0a4-537c503ad631");

    @BeforeEach
    void prepareTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + MODEL_SCHEMA);
            statement.execute("drop table if exists " + MODEL_SCHEMA + "." + TARGET_TABLE);
            statement.execute("drop table if exists " + MODEL_SCHEMA + "." + SOURCE_TABLE);
            statement.execute("create table " + MODEL_SCHEMA + "." + SOURCE_TABLE
                    + " (order_id integer not null, customer_name varchar(100) not null)");
            statement.execute("create table " + MODEL_SCHEMA + "." + TARGET_TABLE
                    + " (order_id bigint primary key, customer_name varchar(100) not null,"
                    + " audit_note varchar(100) not null default 'created')");
            statement.execute("insert into " + MODEL_SCHEMA + "." + SOURCE_TABLE
                    + " values (1, 'Alice'), (2, 'Bob')");
            statement.execute("insert into " + MODEL_SCHEMA + "." + TARGET_TABLE
                    + " values (-1, 'untouched', 'keep-minus-one'), (1, 'stale', 'keep-one')");
        }
    }

    @Test
    void readsAndOverwritesModelsUsingSnapshotPhysicalNamespace() throws Exception {
        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest());

        assertEquals(TaskExecutionState.SUCCESS, result.state());
        assertEquals(2L, result.affectedRows());
        assertNull(result.error());
        assertEquals(List.of("MODEL_INPUT", "MODEL_OUTPUT"),
                result.nodeResults().stream().map(node -> node.nodeType()).toList());
        assertEquals(List.of(NodeExecutionState.SUCCESS, NodeExecutionState.SUCCESS),
                result.nodeResults().stream().map(node -> node.state()).toList());
        assertEquals("模型输入已准备", result.nodeResults().getFirst().message());
        assertEquals(2L, result.nodeResults().getLast().rowsWritten());

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "select order_id, customer_name from " + MODEL_SCHEMA + "." + TARGET_TABLE
                             + " order by order_id")) {
            rows.next();
            assertEquals(1L, rows.getLong(1));
            assertEquals("Alice", rows.getString(2));
            rows.next();
            assertEquals(2L, rows.getLong(1));
            assertEquals("Bob", rows.getString(2));
            assertEquals(false, rows.next());
        }
    }

    @Test
    void upsertsModelsUsingTheModelPrimaryKey() throws Exception {
        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest(JdbcWriteMode.UPSERT));

        assertEquals(TaskExecutionState.SUCCESS, result.state());
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "select order_id, customer_name, audit_note from " + MODEL_SCHEMA + "." + TARGET_TABLE
                             + " order by order_id")) {
            rows.next();
            assertEquals(-1L, rows.getLong(1));
            assertEquals("untouched", rows.getString(2));
            assertEquals("keep-minus-one", rows.getString(3));
            rows.next();
            assertEquals(1L, rows.getLong(1));
            assertEquals("Alice", rows.getString(2));
            assertEquals("keep-one", rows.getString(3));
            rows.next();
            assertEquals(2L, rows.getLong(1));
            assertEquals("Bob", rows.getString(2));
            assertEquals("created", rows.getString(3));
            assertEquals(false, rows.next());
        }
    }

    private TaskExecutionManifest manifest() {
        return manifest(JdbcWriteMode.OVERWRITE);
    }

    private TaskExecutionManifest manifest(JdbcWriteMode writeMode) {
        String inputNodeId = "312dddfd-60ca-4c40-8c08-91a99a3d5c1b";
        String outputNodeId = "129818e4-87dd-4687-8cae-701c3a7ba9c1";
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputNodeId,
                                "订单模型输入",
                                layout(),
                                new ModelInputConfiguration(sourceModelId.toString())
                        ),
                        new ModelOutputNodeDefinition(
                                outputNodeId,
                                "订单模型输出",
                                layout(),
                                new ModelOutputConfiguration(
                                        SOURCE_CODE,
                                        targetModelId.toString(),
                                        writeMode,
                                        List.of(
                                                new JdbcColumnMapping("order_id", "order_id"),
                                                new JdbcColumnMapping("customer_name", "customer_name")
                                        )
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputNodeId, outputNodeId))
        );
        MetadataSnapshot metadata = new MetadataSnapshot(
                List.of(new MetadataDataSource(
                        dataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        CanvasJdbcDatabaseType.POSTGRESQL,
                        Set.of(DataSourcePurpose.STORAGE),
                        List.of()
                )),
                List.of(
                        model(sourceModelId, SOURCE_CODE, SOURCE_TABLE),
                        model(targetModelId, "target_orders", TARGET_TABLE)
                )
        );
        RuntimeDataSource runtime = new RuntimeDataSource(
                dataSourceId,
                RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.STORAGE),
                new RuntimeJdbcConnection(
                        "org.postgresql.Driver",
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getDatabaseName(),
                        "public",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        Map.of()
                )
        );
        Instant now = Instant.now();
        return new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                        now, now.plusSeconds(300)
                ),
                new TaskDefinition(TaskType.CANVAS, definition),
                metadata,
                List.of(runtime)
        );
    }

    private MetadataModel model(UUID id, String code, String physicalTableName) {
        return new MetadataModel(
                id,
                code,
                code,
                1,
                MetadataModelStatus.PUBLISHED,
                MetadataModelPhysicalTableMode.MANAGED,
                dataSourceId,
                POSTGRES.getDatabaseName(),
                MODEL_SCHEMA,
                physicalTableName,
                java.util.stream.Stream.of(
                        id.equals(sourceModelId)
                                ? integerColumn("order_id")
                                : longColumn("order_id"),
                        stringColumn("customer_name"),
                        id.equals(targetModelId) ? nullableStringColumn("audit_note") : null
                ).filter(java.util.Objects::nonNull).toList(),
                List.of(new MetadataUniqueKey(
                        "MODEL_PRIMARY_KEY",
                        MetadataUniqueKeyType.PRIMARY_KEY,
                        List.of("order_id")
                ))
        );
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0.0, 0.0, 240.0, 120.0);
    }

    private static CanvasColumnSchema longColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.LONG, null, null, null,
                false, null, false, false, null
        );
    }

    private static CanvasColumnSchema integerColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.INTEGER, null, null, null,
                false, null, false, false, null
        );
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.STRING, 100, null, null,
                false, null, false, false, null
        );
    }

    private static CanvasColumnSchema nullableStringColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.STRING, 100, null, null,
                true, null, false, false, null
        );
    }
}
