package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasTaskExecutorHttpApiIntegrationTest {

    private static final String BUSINESS_VALUE = "business-row-secret";
    private static final String SIGNING_SECRET = "runner-signing-secret";

    private final UUID apiDataSourceId = UUID.fromString("88e11f62-9010-4232-b876-fd1ae8917721");
    private final UUID resourceId = UUID.fromString("8cf73463-d1b5-4327-bce3-f8e94b965be1");
    private final UUID storageDataSourceId = UUID.fromString("3cf4bb4d-2b2a-40f4-a3f8-206092e3d0c8");
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void readsPaginatedApiPropagatesDeclaredSchemaAndUsesUnifiedNodeResults() throws Exception {
        startServer();

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest());

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertEquals(2, requests.size());
        assertTrue(requests.get(0).contains("customer=" + BUSINESS_VALUE));
        assertTrue(requests.get(0).contains("page=1"));
        assertTrue(requests.get(1).contains("customer=" + BUSINESS_VALUE));
        assertTrue(requests.get(1).contains("page=2"));

        assertEquals(List.of("HTTP_API_INPUT", "JDBC_OUTPUT"),
                result.nodeResults().stream().map(node -> node.nodeType()).toList());
        assertEquals(List.of(NodeExecutionState.SUCCESS, NodeExecutionState.FAILED),
                result.nodeResults().stream().map(node -> node.state()).toList());
        assertEquals(ExecutionFailurePhase.READ, result.nodeResults().getFirst().phase());
        assertEquals("HTTP API 输入已读取", result.nodeResults().getFirst().message());
        assertNull(result.nodeResults().getFirst().error());
        assertEquals(ExecutionFailurePhase.WRITE, result.nodeResults().getLast().phase());
        assertNotNull(result.nodeResults().getLast().error());
        assertEquals("JDBC_CONNECTION_FAILED", result.nodeResults().getLast().error().code());
        assertEquals(result.error(), result.nodeResults().getLast().error());
        assertNotNull(result.error().diagnosticId());

        String serializedResult = result.toString();
        assertFalse(serializedResult.contains(BUSINESS_VALUE));
        assertFalse(serializedResult.contains(SIGNING_SECRET));
        assertFalse(serializedResult.contains("Alice-sensitive"));
        assertFalse(serializedResult.contains("Bob-sensitive"));
    }

    @Test
    void rejectsARuntimeDisabledApiResourceBeforeSendingARequest() throws Exception {
        startServer();
        TaskExecutionManifest base = manifest();
        TaskExecutionManifest manifest = new TaskExecutionManifest(
                base.manifestVersion(),
                base.execution(),
                base.task(),
                base.metadataSnapshot(),
                List.of(apiRuntime(false), unavailableStorageRuntime())
        );

        TaskExecutionResult result = new CanvasTaskExecutor().execute(manifest);

        assertEquals(TaskExecutionState.FAILED, result.state());
        assertTrue(requests.isEmpty());
        assertEquals(1, result.nodeResults().size());
        assertEquals(NodeExecutionState.FAILED, result.nodeResults().getFirst().state());
        assertEquals("API_RESOURCE_DISABLED", result.error().code());
        assertEquals(ExecutionFailurePhase.READ, result.error().phase());
        assertEquals("HTTP_API_INPUT", result.error().nodeType());
        assertEquals(result.error(), result.nodeResults().getFirst().error());
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                requests.add(query);
                if (query != null && query.contains("page=1")) {
                    respond(exchange, "{\"items\":[{\"id\":1,\"name\":\"Alice-sensitive\"}],\"totalPages\":2}");
                } else if (query != null && query.contains("page=2")) {
                    respond(exchange, "{\"items\":[{\"id\":2,\"name\":\"Bob-sensitive\"}],\"totalPages\":2}");
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private TaskExecutionManifest manifest() {
        String inputNodeId = "5f0d16fc-6404-453b-82b0-94bd17b26a78";
        String outputNodeId = "ec1c08b8-a93c-448c-9791-f452885a88d3";
        CanvasDefinition definition = new CanvasDefinition(2, 0, List.of(
                new HttpApiInputNodeDefinition(
                        inputNodeId,
                        "订单 HTTP API 输入",
                        layout(),
                        new HttpApiInputConfiguration(
                                apiDataSourceId.toString(),
                                resourceId.toString(),
                                "api_orders",
                                List.of(new HttpApiContracts.RuntimeParameter("customer", BUSINESS_VALUE)))),
                new JdbcOutputNodeDefinition(
                        outputNodeId,
                        "订单输出",
                        layout(),
                        new JdbcOutputConfiguration(
                                "api_orders",
                                storageDataSourceId.toString(),
                                "orders_target",
                                JdbcWriteMode.APPEND,
                                List.of(
                                        new JdbcColumnMapping("id", "id"),
                                        new JdbcColumnMapping("name", "name")
                                ),
                                List.of()))
        ), List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputNodeId, outputNodeId)));
        List<CanvasColumnSchema> columns = List.of(
                new CanvasColumnSchema(
                        "id", PlatformDataType.INTEGER, null, null, null,
                        false, null, false, false, "订单 ID"),
                new CanvasColumnSchema(
                        "name", PlatformDataType.STRING, 100, null, null,
                        false, null, false, false, "订单名称")
        );
        MetadataSnapshot metadata = new MetadataSnapshot(List.of(
                new MetadataDataSource(
                        apiDataSourceId,
                        true,
                        ConnectionKind.HTTP_API,
                        Set.of(DataSourcePurpose.SOURCE),
                        List.of(new MetadataTable(
                                resourceId.toString(), DatabaseObjectType.API_RESOURCE, columns))),
                new MetadataDataSource(
                        storageDataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        Set.of(DataSourcePurpose.DISTRIBUTION),
                        List.of(new MetadataTable("orders_target", DatabaseObjectType.TABLE, columns)))
        ), List.of());
        Instant now = Instant.now();
        return new TaskExecutionManifest(
                TaskExecutionManifest.CURRENT_MANIFEST_VERSION,
                new TaskExecutionManifest.Execution(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                        now, now.plusSeconds(60)),
                new TaskDefinition(TaskType.CANVAS, definition),
                metadata,
                List.of(apiRuntime(true), unavailableStorageRuntime()));
    }

    private RuntimeDataSource apiRuntime(boolean resourceEnabled) {
        HttpApiContracts.ConnectionConfiguration configuration = new HttpApiContracts.ConnectionConfiguration(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                List.of(),
                2_000,
                5_000,
                0,
                0,
                new HttpApiContracts.NoneAuthentication(),
                true,
                false);
        HttpApiContracts.CredentialBundle credentials = new HttpApiContracts.CredentialBundle(
                null, null, null, null, null, SIGNING_SECRET, null);
        HttpApiContracts.ResourceDefinition resource = new HttpApiContracts.ResourceDefinition(
                resourceId,
                apiDataSourceId,
                "orders",
                "订单",
                HttpApiContracts.GENERIC_CONNECTOR,
                resourceEnabled,
                new HttpApiContracts.RequestTemplate(
                        HttpApiContracts.HttpMethod.GET,
                        "/orders",
                        List.of(new HttpApiContracts.NamedValue("customer", "${runtime.customer}")),
                        List.of(),
                        null),
                HttpApiContracts.SigningConfiguration.none(),
                HttpApiContracts.InvocationType.PAGINATED_REQUEST,
                new HttpApiContracts.PageNumberPagination(
                        HttpApiContracts.ValueLocation.QUERY,
                        "page",
                        "size",
                        1,
                        1,
                        null,
                        "/totalPages"),
                null,
                "/items",
                List.of(
                        new HttpApiContracts.OutputField(
                                "id", "/id",
                                PlatformTypeDefinition.of(
                                        cn.superhuang.data.scalpel.contract.type.PlatformDataType.INTEGER),
                                false, "订单 ID"),
                        new HttpApiContracts.OutputField(
                                "name", "/name",
                                PlatformTypeDefinition.string(100),
                                false, "订单名称")),
                new HttpApiContracts.ExecutionLimits(5, 10, 1024 * 1024, 30));
        return new RuntimeDataSource(
                apiDataSourceId,
                ConnectionKind.HTTP_API,
                null,
                Set.of(DataSourcePurpose.SOURCE),
                null,
                new HttpApiContracts.RuntimeConnection(configuration, credentials),
                List.of(resource));
    }

    private RuntimeDataSource unavailableStorageRuntime() {
        return new RuntimeDataSource(
                storageDataSourceId,
                RuntimeDatabaseType.POSTGRESQL,
                Set.of(DataSourcePurpose.DISTRIBUTION),
                new RuntimeJdbcConnection(
                        "org.postgresql.Driver",
                        "jdbc:postgresql://127.0.0.1:1/unavailable?connectTimeout=1&socketTimeout=1",
                        "unavailable",
                        "public",
                        "test-user",
                        "database-password",
                        Map.of()));
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0.0, 0.0, 240.0, 120.0);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
