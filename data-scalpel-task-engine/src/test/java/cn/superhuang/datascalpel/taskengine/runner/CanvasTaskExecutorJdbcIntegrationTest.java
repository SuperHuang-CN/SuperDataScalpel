package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.JdbcInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumn;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.OutputWritesMetrics;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.MySQLContainer;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "datascalpel.task-execution-it", matches = "true")
class CanvasTaskExecutorJdbcIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("canvas_test")
            .withUsername("canvas")
            .withPassword("canvas-password");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("canvas_test")
            .withUsername("canvas")
            .withPassword("canvas-password");

    private final UUID postgresId = UUID.fromString("3b32930b-79a7-4fdf-8029-8c56cfad51bc");
    private final UUID mysqlId = UUID.fromString("5eeb4a18-d06b-49f3-b9b2-0698fc28c785");
    private final UUID deniedInputId = UUID.fromString("66dca7df-e703-4bba-9fef-4a6b02456558");
    private final UUID deniedOutputId = UUID.fromString("4532ab96-41d8-42e5-811c-7f7a83d457f1");

    @BeforeEach
    void prepareTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("drop table if exists dwd_order_customer_archive");
            statement.execute("drop table if exists dwd_order_customer");
            statement.execute("drop table if exists orders_copy");
            statement.execute("drop table if exists orders");
            statement.execute("drop table if exists short_target");
            statement.execute("drop table if exists short_source");
            statement.execute("create table orders (order_id bigint not null, customer_id bigint not null)");
            statement.execute("create table orders_copy (order_id bigint not null, customer_id bigint not null)");
            statement.execute("create table short_source (value integer not null)");
            statement.execute("create table short_target (value smallint not null)");
            statement.execute("create table dwd_order_customer (order_id bigint not null, customer_id bigint not null, customer_key bigint not null, customer_name varchar(100) not null)");
            statement.execute("create table dwd_order_customer_archive (order_id bigint not null, customer_id bigint not null, customer_key bigint not null, customer_name varchar(100) not null)");
            statement.execute("insert into orders values (1, 101), (2, 102)");
            statement.execute("insert into orders_copy values (-1, -1)");
            statement.execute("insert into short_source values (40000)");
            statement.execute("insert into dwd_order_customer values (-1, -1, -1, 'stale')");
            statement.execute("insert into dwd_order_customer_archive values (-1, -1, -1, 'stale')");
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'canvas_read_denied') then create role canvas_read_denied login password 'read-denied-password'; end if; end $$");
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'canvas_write_denied') then create role canvas_write_denied login password 'write-denied-password'; end if; end $$");
            statement.execute("grant usage on schema public to canvas_read_denied, canvas_write_denied");
            statement.execute("revoke all privileges on all tables in schema public from canvas_read_denied, canvas_write_denied");
            statement.execute("grant select on orders_copy to canvas_write_denied");
        }
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("drop table if exists customers");
            statement.execute("create table customers (customer_key bigint not null, customer_name varchar(100) not null)");
            statement.execute("insert into customers values (101, 'Alice'), (102, 'Bob')");
        }
    }

    @Test
    void executesACrossDatabaseJoinAndGroupsTwoWritesIntoOneOutputNodeResult() throws Exception {
        TaskExecutionManifest manifest = manifest();

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest);

        assertEquals(TaskExecutionState.SUCCESS, result.state());
        assertEquals(TaskExecutionResult.CURRENT_SCHEMA_VERSION, result.schemaVersion());
        assertEquals(4L, result.affectedRows());
        assertNull(result.error());
        assertEquals(4, result.nodeResults().size());
        assertEquals(List.of("JDBC_INPUT", "JDBC_INPUT", "JOIN", "JDBC_OUTPUT"),
                result.nodeResults().stream().map(node -> node.nodeType()).toList());
        assertEquals(List.of(NodeExecutionState.SUCCESS, NodeExecutionState.SUCCESS,
                        NodeExecutionState.SUCCESS, NodeExecutionState.SUCCESS),
                result.nodeResults().stream().map(node -> node.state()).toList());
        assertEquals(4L, result.nodeResults().get(3).rowsWritten());
        OutputWritesMetrics metrics = (OutputWritesMetrics) result.nodeResults().get(3).metrics();
        assertEquals(2, metrics.writes().size());
        assertEquals(List.of(2L, 2L), metrics.writes().stream()
                .map(write -> write.affectedRows()).toList());
        assertOutputRows("dwd_order_customer");
        assertOutputRows("dwd_order_customer_archive");
    }

    @Test
    void reportsPostgresSelectPermissionFailureOnInputNode() {
        String inputNodeId = "14f085f5-1670-4b1a-829b-04da7fc38146";
        TaskExecutionResult result = new CanvasTaskExecutor().execute(transferManifest(
                inputNodeId,
                "21e48893-b3f5-42ac-817c-caa807a0e63e",
                deniedInputId,
                postgresId,
                postgresRuntime(
                        deniedInputId,
                        Set.of(DataSourcePurpose.SOURCE),
                        "canvas_read_denied",
                        "read-denied-password"),
                postgresRuntime(
                        postgresId,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())));

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals(1, result.nodeResults().size());
        assertEquals(NodeExecutionState.FAILED, result.nodeResults().getFirst().state());
        assertPermissionFailure(result, inputNodeId, "JDBC_INPUT", ExecutionFailurePhase.READ);
    }

    @Test
    void reportsPostgresTruncatePermissionFailureOnOutputNode() {
        String inputNodeId = "754c49b3-1705-4838-be61-fc7ca89acc36";
        String outputNodeId = "df3f8ba4-55cc-4b7e-9d31-41d351a63b50";
        TaskExecutionResult result = new CanvasTaskExecutor().execute(transferManifest(
                inputNodeId,
                outputNodeId,
                postgresId,
                deniedOutputId,
                postgresRuntime(
                        postgresId,
                        Set.of(DataSourcePurpose.SOURCE),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                postgresRuntime(
                        deniedOutputId,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        "canvas_write_denied",
                        "write-denied-password")));

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals(2, result.nodeResults().size());
        assertEquals(NodeExecutionState.SUCCESS, result.nodeResults().get(0).state());
        assertEquals(NodeExecutionState.FAILED, result.nodeResults().get(1).state());
        assertPermissionFailure(result, outputNodeId, "JDBC_OUTPUT", ExecutionFailurePhase.WRITE);
    }

    @Test
    void allowsRuntimeInputTypeDifferenceAndReportsTheActualWriteFailure() {
        String inputNodeId = "f12de4fa-3bb4-4ba0-a98c-8fd39b273601";
        String outputNodeId = "179377d4-e2cf-47c0-8c7f-bac03e4430cf";
        TaskExecutionManifest base = transferManifest(
                inputNodeId,
                outputNodeId,
                postgresId,
                deniedOutputId,
                postgresRuntime(
                        postgresId,
                        Set.of(DataSourcePurpose.SOURCE),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                postgresRuntime(
                        deniedOutputId,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        "canvas_write_denied",
                        "write-denied-password"));
        MetadataSnapshot driftedMetadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        postgresId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(table("orders", integerColumn("order_id"), longColumn("customer_id")))),
                new MetadataDataSource(
                        deniedOutputId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        List.of(table("orders_copy", longColumn("order_id"), longColumn("customer_id"))))
        ), List.of());
        TaskExecutionManifest manifest = new TaskExecutionManifest(
                base.manifestVersion(),
                base.execution(),
                base.task(),
                driftedMetadata,
                base.runtimeDataSources()
        );

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest);

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals(2, result.nodeResults().size());
        assertEquals(NodeExecutionState.SUCCESS, result.nodeResults().getFirst().state());
        assertEquals(NodeExecutionState.FAILED, result.nodeResults().get(1).state());
        assertPermissionFailure(result, outputNodeId, "JDBC_OUTPUT", ExecutionFailurePhase.WRITE);
    }

    @Test
    void explicitlyCastsAnInRangeIntegerAndWritesItAsShort() throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("update short_source set value = 123");
        }
        TaskExecutionManifest manifest = narrowingManifest(
                "f783653c-8272-4507-ab23-a604c6b2a0da",
                "d2f7232b-306c-4916-bf1d-accc02da79a9");

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest);

        assertEquals(TaskExecutionState.SUCCESS, result.state());
        assertNull(result.error());
        assertEquals(1L, result.affectedRows());
        assertEquals(1L, result.nodeResults().getLast().rowsWritten());
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select value from short_target")) {
            rows.next();
            assertEquals(123, rows.getShort(1));
            assertFalse(rows.next());
        }
    }

    @Test
    void turnsAnsiIntegerToShortOverflowIntoAStructuredOutputFailure() {
        String inputNodeId = "34ab998d-3a51-43da-aafe-041391b371d2";
        String outputNodeId = "d33c0db1-d7d3-4fa0-a0c8-45a68e9a88fb";
        TaskExecutionManifest manifest = narrowingManifest(inputNodeId, outputNodeId);

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest);

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals(2, result.nodeResults().size());
        assertEquals(NodeExecutionState.SUCCESS, result.nodeResults().getFirst().state());
        assertEquals(NodeExecutionState.FAILED, result.nodeResults().getLast().state());
        assertEquals("JDBC_OUTPUT_FAILED", result.error().code());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, result.error().category());
        assertFalse(result.error().retryable());
        assertEquals(outputNodeId, result.error().nodeId());
        assertEquals("JDBC_OUTPUT", result.error().nodeType());
        assertEquals(ExecutionFailurePhase.WRITE, result.error().phase());
        assertNotNull(result.error().diagnosticId());
        assertEquals(result.error(), result.nodeResults().getLast().error());
    }

    private static void assertPermissionFailure(
            TaskExecutionResult result,
            String nodeId,
            String nodeType,
            ExecutionFailurePhase phase
    ) {
        assertNotNull(result.error());
        assertEquals("JDBC_PERMISSION_DENIED", result.error().code());
        assertEquals(ExecutionErrorCategory.PERMISSION, result.error().category());
        assertFalse(result.error().retryable());
        assertEquals(nodeId, result.error().nodeId());
        assertEquals(nodeType, result.error().nodeType());
        assertEquals(phase, result.error().phase());
        assertEquals("42501", result.error().sqlState());
        assertNotNull(result.error().diagnosticId());
        assertEquals(result.error(), result.nodeResults().getLast().error());
    }

    private void assertOutputRows(String tableName) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "select order_id, customer_name from " + tableName + " order by order_id")) {
            rows.next();
            assertEquals(1L, rows.getLong(1));
            assertEquals("Alice", rows.getString(2));
            rows.next();
            assertEquals(2L, rows.getLong(1));
            assertEquals("Bob", rows.getString(2));
            assertEquals(false, rows.next());
        }
    }

    private TaskExecutionManifest transferManifest(
            String inputNodeId,
            String outputNodeId,
            UUID inputDataSourceId,
            UUID outputDataSourceId,
            RuntimeDataSource inputRuntime,
            RuntimeDataSource outputRuntime
    ) {
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                new JdbcInputNodeDefinition(
                        inputNodeId,
                        "订单输入",
                        layout(),
                        new JdbcInputConfiguration(
                                inputDataSourceId.toString(), List.of(new JdbcInputTableSelection("orders")))),
                new JdbcOutputNodeDefinition(
                        outputNodeId,
                        "订单输出",
                        layout(),
                        new JdbcOutputConfiguration(
                                "orders",
                                outputDataSourceId.toString(),
                                "orders_copy",
                                JdbcWriteMode.OVERWRITE,
                                List.of(
                                        new JdbcColumnMapping("order_id", "order_id"),
                                        new JdbcColumnMapping("customer_id", "customer_id")
                                ),
                                List.of()))
        ), List.of(edge(inputNodeId, outputNodeId)));
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        inputDataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(table("orders", longColumn("order_id"), longColumn("customer_id")))),
                new MetadataDataSource(
                        outputDataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        List.of(table("orders_copy", longColumn("order_id"), longColumn("customer_id"))))
        ), List.of());
        Instant now = Instant.now();
        return new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, now, now.plusSeconds(300)),
                new TaskDefinition(TaskType.CANVAS, definition),
                metadata,
                List.of(inputRuntime, outputRuntime));
    }

    private TaskExecutionManifest narrowingManifest(
            String inputNodeId,
            String outputNodeId
    ) {
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                new JdbcInputNodeDefinition(
                        inputNodeId,
                        "整数输入",
                        layout(),
                        new JdbcInputConfiguration(
                                postgresId.toString(), List.of(new JdbcInputTableSelection("short_source")))),
                new JdbcOutputNodeDefinition(
                        outputNodeId,
                        "短整型输出",
                        layout(),
                        new JdbcOutputConfiguration(
                                "short_source",
                                postgresId.toString(),
                                "short_target",
                                JdbcWriteMode.APPEND,
                                List.of(new JdbcColumnMapping("value", "value")),
                                List.of()))
        ), List.of(edge(inputNodeId, outputNodeId)));
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        postgresId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                        List.of(
                                table("short_source", integerColumn("value")),
                                table("short_target", shortColumn("value"))))
        ), List.of());
        RuntimeDataSource runtime = postgresRuntime(
                postgresId,
                Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Instant now = Instant.now();
        return new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                        now, now.plusSeconds(300)),
                new TaskDefinition(TaskType.CANVAS, definition),
                metadata,
                List.of(runtime));
    }

    private RuntimeDataSource postgresRuntime(
            UUID id,
            Set<DataSourcePurpose> purposes,
            String username,
            String password
    ) {
        return new RuntimeDataSource(
                id,
                RuntimeDatabaseType.POSTGRESQL,
                purposes,
                new RuntimeJdbcConnection(
                        "org.postgresql.Driver",
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getDatabaseName(),
                        "public",
                        username,
                        password,
                        Map.of()));
    }

    private TaskExecutionManifest manifest() {
        String ordersNode = "d2bd1059-6279-4839-a177-853f88e45ca5";
        String customersNode = "e059d8fd-5cfc-4b4c-968b-3cbfe171a70a";
        String joinNode = "e55c50d7-374d-4fe0-aede-e1648988af23";
        String outputNode = "79d6f66b-a007-4984-8042-a612cb38ba82";
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                new JdbcInputNodeDefinition(
                        ordersNode, "订单输入", layout(),
                        new JdbcInputConfiguration(
                                postgresId.toString(), List.of(new JdbcInputTableSelection("orders")))),
                new JdbcInputNodeDefinition(
                        customersNode, "客户输入", layout(),
                        new JdbcInputConfiguration(
                                mysqlId.toString(), List.of(new JdbcInputTableSelection("customers")))),
                new JoinNodeDefinition(
                        joinNode, "订单客户 Join", layout(),
                        new JoinConfiguration(
                                "orders", "customers", "order_customer", JoinType.INNER,
                                List.of(new JoinCondition("customer_id", JoinOperator.EQUALS, "customer_key")),
                                List.of(
                                        new JoinOutputColumn(JoinOutputColumnSource.LEFT, "order_id", "order_id", true),
                                        new JoinOutputColumn(JoinOutputColumnSource.LEFT, "customer_id", "customer_id", true),
                                        new JoinOutputColumn(JoinOutputColumnSource.RIGHT, "customer_key", "customer_key", true),
                                        new JoinOutputColumn(JoinOutputColumnSource.RIGHT, "customer_name", "customer_name", true)))),
                new JdbcOutputNodeDefinition(
                        outputNode, "结果输出", layout(),
                        new JdbcOutputConfiguration(
                                postgresId.toString(),
                                List.of(
                                        new JdbcOutputWrite(
                                                UUID.randomUUID().toString(), "order_customer",
                                                "dwd_order_customer", JdbcWriteMode.OVERWRITE,
                                                orderCustomerMappings(), List.of()),
                                        new JdbcOutputWrite(
                                                UUID.randomUUID().toString(), "order_customer",
                                                "dwd_order_customer_archive", JdbcWriteMode.OVERWRITE,
                                                orderCustomerMappings(), List.of()))))
        ), List.of(
                edge(ordersNode, joinNode),
                edge(customersNode, joinNode),
                edge(joinNode, outputNode)
        ));
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        postgresId, true, ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                        List.of(
                                table("orders", longColumn("order_id"), longColumn("customer_id")),
                                table("dwd_order_customer", longColumn("order_id"), longColumn("customer_id"),
                                        longColumn("customer_key"), stringColumn("customer_name")),
                                table("dwd_order_customer_archive", longColumn("order_id"), longColumn("customer_id"),
                                        longColumn("customer_key"), stringColumn("customer_name")))),
                new MetadataDataSource(
                        mysqlId, true, ConnectionKind.JDBC, Set.of(DataSourcePurpose.SOURCE),
                        List.of(table("customers", longColumn("customer_key"), stringColumn("customer_name"))))
        ), List.of());
        RuntimeDataSource postgres = new RuntimeDataSource(
                postgresId, RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                new RuntimeJdbcConnection(
                        "org.postgresql.Driver", POSTGRES.getJdbcUrl(), POSTGRES.getDatabaseName(), "public",
                        POSTGRES.getUsername(), POSTGRES.getPassword(), Map.of()));
        RuntimeDataSource mysql = new RuntimeDataSource(
                mysqlId, RuntimeDatabaseType.MYSQL, Set.of(DataSourcePurpose.SOURCE),
                new RuntimeJdbcConnection(
                        "com.mysql.cj.jdbc.Driver", MYSQL.getJdbcUrl(), MYSQL.getDatabaseName(), null,
                        MYSQL.getUsername(), MYSQL.getPassword(), Map.of()));
        Instant now = Instant.now();
        return new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1, now, now.plusSeconds(300)),
                new TaskDefinition(TaskType.CANVAS, definition),
                metadata,
                List.of(postgres, mysql));
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0.0, 0.0, 240.0, 120.0);
    }

    private static List<JdbcColumnMapping> orderCustomerMappings() {
        return List.of(
                new JdbcColumnMapping("order_id", "order_id"),
                new JdbcColumnMapping("customer_id", "customer_id"),
                new JdbcColumnMapping("customer_key", "customer_key"),
                new JdbcColumnMapping("customer_name", "customer_name")
        );
    }

    private static CanvasEdgeDefinition edge(String source, String target) {
        return new CanvasEdgeDefinition(UUID.randomUUID().toString(), source, target);
    }

    private static MetadataTable table(String name, CanvasColumnSchema... columns) {
        return new MetadataTable(name, DatabaseObjectType.TABLE, List.of(columns));
    }

    private static CanvasColumnSchema longColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.LONG, null, null, null,
                false, null, false, false, null);
    }

    private static CanvasColumnSchema integerColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.INTEGER, null, null, null,
                false, null, false, false, null);
    }

    private static CanvasColumnSchema shortColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.SHORT, null, null, null,
                false, null, false, false, null);
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return new CanvasColumnSchema(
                name, PlatformDataType.STRING, 100, null, null,
                false, null, false, false, null);
    }
}
