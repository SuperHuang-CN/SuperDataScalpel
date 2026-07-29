package cn.superhuang.data.scalpel.admin.datasource;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.ApiResourceRepository;
import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(DataSourceIntegrationTests.StubDatabaseInspectorConfiguration.class)
class DataSourceIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private DataSourceRepository repository;

    @Autowired
    private ApiResourceRepository apiResourceRepository;

    private MockMvc mockMvc;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String accessToken = loginAsAdministrator(mockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
        apiResourceRepository.deleteAll();
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        apiResourceRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void createsSearchesUpdatesAndDeletesDataSourcesWithoutReturningPasswords() throws Exception {
        String created = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "Business_PostgreSQL",
                                  "name": "业务 PostgreSQL",
                                  "purposes": ["SOURCE", "STORAGE", "DISTRIBUTION"],
                                  "type": "POSTGRESQL",
                                  "enabled": true,
                                  "description": "业务主库",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "192.168.1.10",
                                    "port": 5432,
                                    "databaseName": "business",
                                    "schemaName": "public",
                                    "username": "datascalpel",
                                    "password": "secret-value",
                                    "options": {
                                      "sslmode": "prefer",
                                      "tcpKeepAlive": "true"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("business_postgresql"))
                .andExpect(jsonPath("$.purposes.length()").value(3))
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true))
                .andExpect(jsonPath("$.connection.options.sslmode").value("prefer"))
                .andExpect(jsonPath("$.connection.options.tcpKeepAlive").value("true"))
                .andExpect(jsonPath("$.connection.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/v1/data-sources")
                        .param("search", "sourceEnabled:\"true\" AND storageEnabled:\"true\" AND distributionEnabled:\"true\"")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "-updatedAt,code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("business_postgresql"));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/update", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "业务 PostgreSQL（已更新）",
                                  "purposes": ["STORAGE"],
                                  "type": "POSTGRESQL",
                                  "enabled": false,
                                  "description": "更新后说明",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "192.168.1.11",
                                    "port": 5432,
                                    "databaseName": "business",
                                    "schemaName": "public",
                                    "username": "datascalpel"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("business_postgresql"))
                .andExpect(jsonPath("$.purposes.length()").value(1))
                .andExpect(jsonPath("$.purposes[0]").value("STORAGE"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.connection.host").value("192.168.1.11"))
                .andExpect(jsonPath("$.connection.options.sslmode").value("prefer"))
                .andExpect(jsonPath("$.connection.options.tcpKeepAlive").value("true"))
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/update", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "业务 PostgreSQL（已更新）",
                                  "purposes": ["STORAGE"],
                                  "type": "POSTGRESQL",
                                  "enabled": false,
                                  "description": "更新后说明",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "192.168.1.11",
                                    "port": 5432,
                                    "databaseName": "business",
                                    "schemaName": "public",
                                    "username": "datascalpel",
                                    "options": {}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.options").isEmpty())
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/data-sources/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesInputRejectsDuplicateCodesAndKeepsTheTestApiStable() throws Exception {
        mockMvc.perform(get("/api/v1/data-source-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[?(@.id == 'POSTGRESQL')].defaultPort").value(hasItem(5432)));

        String createRequest = """
                {
                  "code": "warehouse",
                  "name": "数仓",
                  "purposes": ["STORAGE"],
                  "type": "DAMENG",
                  "connection": {
                    "kind": "JDBC",
                    "host": "localhost",
                    "port": 5236,
                    "databaseName": "warehouse",
                    "username": "system"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "invalid",
                                  "name": "无用途连接",
                                  "purposes": [],
                                  "type": "MYSQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost",
                                    "port": 3306,
                                    "databaseName": "test",
                                    "username": "root"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/data-sources/actions/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "MYSQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost",
                                    "port": 3306,
                                    "databaseName": "test",
                                    "username": "root"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.databaseProduct").value("Test Database"))
                .andExpect(jsonPath("$.message").value("连接测试成功"));
    }

    @Test
    void returnsRedactedTechnicalDiagnosticsWhenConnectionTestFails() throws Exception {
        mockMvc.perform(post("/api/v1/data-sources/actions/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "MYSQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "failure.test",
                                    "port": 3306,
                                    "databaseName": "test",
                                    "username": "root",
                                    "password": "draft-secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NETWORK_ERROR"))
                .andExpect(jsonPath("$.message").value("连接失败，请检查主机、端口和网络"))
                .andExpect(jsonPath("$.diagnostic.exceptionType").value(SQLException.class.getName()))
                .andExpect(jsonPath("$.diagnostic.rawMessage")
                        .value("Communications link failure using password [REDACTED]"))
                .andExpect(jsonPath("$.diagnostic.sqlState").value("08S01"))
                .andExpect(jsonPath("$.diagnostic.vendorCode").value(0))
                .andExpect(jsonPath("$.diagnostic.causes[0].exceptionType")
                        .value(ConnectException.class.getName()))
                .andExpect(jsonPath("$.diagnostic.causes[0].message").value("Connection refused"));
    }

    @Test
    void rejectsJdbcOptionsThatDoNotFitThePersistenceColumn() throws Exception {
        String options = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> "\"custom" + index + "\":\"" + "x".repeat(512) + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "oversized_options",
                                  "name": "超长参数",
                                  "purposes": ["SOURCE"],
                                  "type": "POSTGRESQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost",
                                    "port": 5432,
                                    "databaseName": "test",
                                    "username": "reader",
                                    "options": {%s}
                                  }
                                }
                                """.formatted(options)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("JDBC 连接参数编码后不能超过 4000 个字符"));
    }

    @Test
    void rejectsProtectedSensitiveAndCaseInsensitiveDuplicateJdbcOptions() throws Exception {
        assertInvalidOptions(
                "protected_options",
                "\"jdbcUrl\":\"jdbc:postgresql://other/database\"",
                "由系统管理"
        );
        assertInvalidOptions(
                "sensitive_options",
                "\"apiToken\":\"do-not-store\"",
                "敏感 JDBC 连接参数"
        );
        assertInvalidOptions(
                "duplicate_options",
                "\"tcpKeepAlive\":\"true\",\"TCPKEEPALIVE\":\"false\"",
                "参数名不能重复"
        );
    }

    private void assertInvalidOptions(String code, String options, String expectedDetail) throws Exception {
        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "无效参数",
                                  "purposes": ["SOURCE"],
                                  "type": "POSTGRESQL",
                                  "connection": {
                                    "kind": "JDBC",
                                    "host": "localhost",
                                    "port": 5432,
                                    "databaseName": "test",
                                    "username": "reader",
                                    "options": {%s}
                                  }
                                }
                                """.formatted(code, options)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(containsString(expectedDetail)));
    }

    @Test
    void managesKafkaAndS3RegistrationsWithoutEnablingTheirRuntimeClients() throws Exception {
        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "event_cluster",
                                  "name": "事件 Kafka 集群",
                                  "purposes": ["SOURCE", "DISTRIBUTION"],
                                  "type": "KAFKA",
                                  "connection": {
                                    "kind": "KAFKA",
                                    "bootstrapServers": "kafka-1.internal:9092,kafka-2.internal:9092",
                                    "securityProtocol": "SASL_SSL",
                                    "saslMechanism": "SCRAM-SHA-512",
                                    "username": "processor",
                                    "password": "kafka-secret"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("KAFKA"))
                .andExpect(jsonPath("$.connection.kind").value("KAFKA"))
                .andExpect(jsonPath("$.connection.bootstrapServers").value("kafka-1.internal:9092,kafka-2.internal:9092"))
                .andExpect(jsonPath("$.connection.passwordConfigured").value(true))
                .andExpect(jsonPath("$.connection.password").doesNotExist());

        String s3Response = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "archive_bucket",
                                  "name": "归档对象存储",
                                  "purposes": ["SOURCE", "DISTRIBUTION"],
                                  "type": "S3",
                                  "connection": {
                                    "kind": "S3",
                                    "endpoint": "https://minio.internal",
                                    "region": "cn-north-1",
                                    "bucket": "data-archive",
                                    "rootPrefix": "/projects/2026/",
                                    "accessKey": "archive-user",
                                    "secretKey": "s3-secret",
                                    "pathStyleAccess": true
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("S3"))
                .andExpect(jsonPath("$.connection.kind").value("S3"))
                .andExpect(jsonPath("$.connection.bucket").value("data-archive"))
                .andExpect(jsonPath("$.connection.rootPrefix").value("projects/2026"))
                .andExpect(jsonPath("$.connection.secretKeyConfigured").value(true))
                .andExpect(jsonPath("$.connection.secretKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String s3Id = com.jayway.jsonpath.JsonPath.read(s3Response, "$.id");
        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/test", s3Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S3_CONNECTION_FAILED"));

        mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "invalid_s3_storage",
                                  "name": "无效 S3 存储",
                                  "purposes": ["STORAGE"],
                                  "type": "S3",
                                  "connection": {
                                    "kind": "S3",
                                    "endpoint": "https://minio.internal",
                                    "bucket": "data-archive",
                                    "accessKey": "archive-user",
                                    "secretKey": "s3-secret"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void managesHttpApiDataSourceAndResourcesWithoutExposingCredentials() throws Exception {
        String dataSourceBody = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "partner_api",
                                  "name": "合作方订单 API",
                                  "purposes": ["SOURCE"],
                                  "type": "HTTP_API",
                                  "enabled": true,
                                  "connection": {
                                    "kind": "HTTP_API",
                                    "baseUrl": "https://partner.example.test/v1/",
                                    "defaultHeaders": [{"name":"X-Client-Version","value":"2026-07"}],
                                    "connectTimeoutMs": 2000,
                                    "requestTimeoutMs": 10000,
                                    "minimumRequestIntervalMs": 10,
                                    "maxRetries": 2,
                                    "authentication": {
                                      "type": "OAUTH2_CLIENT_CREDENTIALS",
                                      "tokenUrl": "https://partner.example.test/oauth/token",
                                      "clientId": "datascalpel-client",
                                      "clientSecret": "oauth-client-secret",
                                      "scopes": ["orders.read"],
                                      "tokenLocation": "HEADER",
                                      "tokenName": "Authorization",
                                      "tokenValueTemplate": "Bearer ${token}"
                                    },
                                    "signingSecret": "resource-signing-secret"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("HTTP_API"))
                .andExpect(jsonPath("$.connection.kind").value("HTTP_API"))
                .andExpect(jsonPath("$.connection.configuration.baseUrl")
                        .value("https://partner.example.test/v1"))
                .andExpect(jsonPath("$.connection.configuration.authentication.type")
                        .value("OAUTH2_CLIENT_CREDENTIALS"))
                .andExpect(jsonPath("$.connection.configuration.authentication.clientSecretConfigured")
                        .value(true))
                .andExpect(jsonPath("$.connection.configuration.signingSecretConfigured").value(true))
                .andExpect(jsonPath("$.connection.configuration.authentication.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.connection.credentials").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(dataSourceBody.contains("oauth-client-secret"));
        assertFalse(dataSourceBody.contains("resource-signing-secret"));
        String dataSourceId = com.jayway.jsonpath.JsonPath.read(dataSourceBody, "$.id");

        var storedConnection = repository.findById(UUID.fromString(dataSourceId)).orElseThrow().getConnection();
        assertFalse(storedConnection.apiCredentialsCiphertextValue().contains("oauth-client-secret"));
        assertFalse(storedConnection.apiCredentialsCiphertextValue().contains("resource-signing-secret"));
        assertNotEquals("oauth-client-secret", storedConnection.apiCredentialsCiphertextValue());

        String resourceBody = mockMvc.perform(post(
                        "/api/v1/data-sources/{dataSourceId}/api-resources", dataSourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "orders",
                                  "name": "订单列表",
                                  "connectorType": "GENERIC_HTTP",
                                  "enabled": true,
                                  "request": {
                                    "method": "GET",
                                    "path": "/orders",
                                    "queryParameters": [{"name":"startDate","value":"${runtime.startDate}"}],
                                    "headers": [],
                                    "bodyTemplate": null
                                  },
                                  "signing": {
                                    "type": "HMAC_SHA256",
                                    "canonicalTemplate": "${request.method}\\n${request.path}\\n${request.query}\\n${timestamp}\\n${nonce}",
                                    "timestamp": {"name":"X-Timestamp","location":"HEADER","unit":"MILLISECONDS"},
                                    "nonce": {"name":"X-Nonce","location":"HEADER"},
                                    "output": {"name":"X-Signature","location":"HEADER","encoding":"HEX_LOWERCASE","valueTemplate":"${signature}"}
                                  },
                                  "invocationType": "PAGINATED_REQUEST",
                                  "pagination": {
                                    "type": "CURSOR",
                                    "location": "QUERY",
                                    "cursorParameter": "cursor",
                                    "initialCursor": null,
                                    "nextCursorPointer": "/meta/nextCursor",
                                    "hasMorePointer": "/meta/hasMore"
                                  },
                                  "asyncJob": null,
                                  "recordsPointer": "/data/items",
                                  "outputFields": [{
                                    "name": "id",
                                    "jsonPointer": "/id",
                                    "type": {"type":"STRING","length":64},
                                    "nullable": false,
                                    "comment": "订单 ID"
                                  }],
                                  "limits": {
                                    "maxPages": 100,
                                    "maxRows": 100000,
                                    "maxResponseBytes": 10485760,
                                    "maxDurationSeconds": 300
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("orders"))
                .andExpect(jsonPath("$.request.path").value("/orders"))
                .andExpect(jsonPath("$.signing.type").value("HMAC_SHA256"))
                .andExpect(jsonPath("$.pagination.type").value("CURSOR"))
                .andExpect(jsonPath("$.outputFields[0].type.type").value("STRING"))
                .andReturn().getResponse().getContentAsString();
        String resourceId = com.jayway.jsonpath.JsonPath.read(resourceBody, "$.id");

        mockMvc.perform(get("/api/v1/data-sources/{dataSourceId}/api-resources", dataSourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(resourceId));

        mockMvc.perform(get(
                        "/api/v1/data-sources/{dataSourceId}/api-resources/{resourceId}",
                        dataSourceId, resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsPointer").value("/data/items"));

        mockMvc.perform(post(
                        "/api/v1/data-sources/{dataSourceId}/api-resources/{resourceId}/actions/update",
                        dataSourceId, resourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resourceBody.replace("\"订单列表\"", "\"订单列表（已更新）\"")
                                .replace("\"enabled\":true", "\"enabled\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("订单列表（已更新）"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", dataSourceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("数据源下存在 API 资源，不能删除"));

        mockMvc.perform(post(
                        "/api/v1/data-sources/{dataSourceId}/api-resources/{resourceId}/actions/delete",
                        dataSourceId, resourceId))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/data-sources/{id}/actions/delete", dataSourceId))
                .andExpect(status().isNoContent());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void returnsHttpApiResourceDiagnosticsAndLogsASanitizedStackTrace(CapturedOutput output) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/items", exchange -> {
            try {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                String signature = exchange.getRequestHeaders().getFirst("X-Signature");
                byte[] body = ("{\"authorization\":\"" + authorization
                        + "\",\"signature\":\"" + signature
                        + "\",\"secret\":\"diagnostic-signing-secret\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        String source = mockMvc.perform(post("/api/v1/data-sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "diagnostic_api",
                                  "name": "诊断 API",
                                  "purposes": ["SOURCE"],
                                  "type": "HTTP_API",
                                  "connection": {
                                    "kind": "HTTP_API",
                                    "baseUrl": "%s",
                                    "authentication": {
                                      "type": "BEARER_TOKEN",
                                      "token": "diagnostic-bearer-secret"
                                    },
                                    "signingSecret": "diagnostic-signing-secret"
                                  }
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String dataSourceId = com.jayway.jsonpath.JsonPath.read(source, "$.id");
        String resource = mockMvc.perform(post(
                        "/api/v1/data-sources/{dataSourceId}/api-resources", dataSourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "failing_resource",
                                  "name": "失败资源",
                                  "connectorType": "GENERIC_HTTP",
                                  "enabled": true,
                                  "request": {"method":"GET","path":"/items","queryParameters":[],"headers":[],"bodyTemplate":null},
                                  "signing": {
                                    "type": "HMAC_SHA256",
                                    "canonicalTemplate": "${request.path}|${credential.signingSecret}",
                                    "timestamp": null,
                                    "nonce": null,
                                    "output": {"name":"X-Signature","location":"HEADER","encoding":"HEX_LOWERCASE","valueTemplate":null}
                                  },
                                  "invocationType": "SINGLE_REQUEST",
                                  "pagination": {"type":"NONE"},
                                  "asyncJob": null,
                                  "recordsPointer": "/items",
                                  "outputFields": [{"name":"id","jsonPointer":"/id","type":{"type":"STRING"},"nullable":false,"comment":null}],
                                  "limits": {"maxPages":1,"maxRows":100,"maxResponseBytes":1048576,"maxDurationSeconds":30}
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String resourceId = com.jayway.jsonpath.JsonPath.read(resource, "$.id");

        String testResult = mockMvc.perform(post(
                        "/api/v1/data-sources/{dataSourceId}/api-resources/{resourceId}/actions/test",
                        dataSourceId, resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("API_HTTP_ERROR"))
                .andExpect(jsonPath("$.diagnostic.exceptionType")
                        .value("cn.superhuang.data.scalpel.business.datasource.service.http.HttpApiExecutionException"))
                .andExpect(jsonPath("$.diagnostic.rawMessage").value("API 请求失败，HTTP 500"))
                .andExpect(jsonPath("$.diagnostic.httpStatus").value(500))
                .andExpect(jsonPath("$.diagnostic.responsePreview").value(containsString("[REDACTED]")))
                .andReturn().getResponse().getContentAsString();

        assertFalse(testResult.contains("diagnostic-bearer-secret"));
        assertFalse(testResult.contains("diagnostic-signing-secret"));
        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("HTTP API resource test failed")
                .contains("HttpApiExecutionException: API 请求失败，HTTP 500")
                .doesNotContain("diagnostic-bearer-secret")
                .doesNotContain("diagnostic-signing-secret");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubDatabaseInspectorConfiguration {

        @Bean
        @Primary
        DatabaseInspector stubDatabaseInspector() {
            return new DatabaseInspector(BuiltInDialects.registry(), new JdbcConnectionFactory()) {
                @Override
                public ConnectionCheck test(String databaseType, JdbcConnectionConfig config) {
                    if ("failure.test".equals(config.host())) {
                        SQLException failure = new SQLException(
                                "Communications link failure using password " + config.password(),
                                "08S01",
                                0
                        );
                        failure.initCause(new ConnectException("Connection refused"));
                        throw new DatabaseAccessException(
                                "NETWORK_ERROR",
                                "连接失败，请检查主机、端口和网络",
                                failure
                        );
                    }
                    return new ConnectionCheck(
                            true,
                            "SUCCESS",
                            "连接测试成功",
                            12,
                            "Test Database",
                            "1.0",
                            "Test JDBC Driver"
                    );
                }
            };
        }
    }
}
