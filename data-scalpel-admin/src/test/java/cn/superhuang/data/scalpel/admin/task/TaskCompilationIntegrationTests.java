package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class TaskCompilationIntegrationTests {

    private static final String REQUEST_ID = "a39bb068-bbb9-40b0-8136-1b1adecc3953";
    private static final String DATA_SOURCE_ID = "55859069-6387-4390-b850-104845ee5370";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private SystemConfigurationRepository configurationRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final AtomicInteger engineStatus = new AtomicInteger(200);
    private final AtomicReference<String> engineRequestBody = new AtomicReference<>();
    private final AtomicReference<String> engineAuthorization = new AtomicReference<>();
    private final AtomicReference<String> enginePath = new AtomicReference<>();
    private HttpServer engine;
    private MockMvc authenticatedMvc;
    private MockMvc rawMvc;

    @BeforeEach
    void setUp() throws Exception {
        engineStatus.set(200);
        engineRequestBody.set(null);
        engineAuthorization.set(null);
        enginePath.set(null);
        engine = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        engine.createContext("/", this::handleEngineRequest);
        engine.start();

        var configuration = configurationRepository.findByConfigKey("task.engine.base-url").orElseThrow();
        configuration.updateValue("http://127.0.0.1:" + engine.getAddress().getPort());
        configurationRepository.saveAndFlush(configuration);

        rawMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
        String accessToken = loginAsAdministrator(rawMvc);
        authenticatedMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) engine.stop(0);
    }

    @Test
    void forwardsTypedCanvasCompilationAndReturnsEngineBusinessValidation() throws Exception {
        String response = authenticatedMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.nodeResults[0].state").value("ERROR"))
                .andExpect(jsonPath("$.nodeResults[0].outputTables[0].columns[0].fieldType").value("LONG"))
                .andReturn().getResponse().getContentAsString();

        assertThat(engineAuthorization.get()).isEqualTo("Bearer test-task-engine-token");
        assertThat(enginePath.get()).isEqualTo("/api/v1/task-compilations");
        assertThat(JsonPath.<String>read(engineRequestBody.get(), "$.task.definition.nodes[0].type"))
                .isEqualTo("JDBC_INPUT");
        assertThat(JsonPath.<String>read(engineRequestBody.get(), "$.task.definition.nodes[1].type"))
                .isEqualTo("JOIN");
        assertThat(JsonPath.<String>read(engineRequestBody.get(), "$.task.definition.nodes[2].type"))
                .isEqualTo("JDBC_OUTPUT");
        assertThat(JsonPath.<List<String>>read(engineRequestBody.get(), "$.metadataSnapshot.dataSources[0].purposes"))
                .containsExactly("SOURCE", "DISTRIBUTION");
        assertThat(JsonPath.<String>read(response, "$.canvasIssues[0].code")).isEqualTo("INVALID_NODE_DEGREE");
    }

    @Test
    void rejectsMalformedTypedContractsBeforeCallingTheEngine() throws Exception {
        authenticatedMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest().replace("JDBC_INPUT", "UNKNOWN_NODE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        authenticatedMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest().replace(REQUEST_ID, "not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        assertThat(engineRequestBody.get()).isNull();
    }

    @Test
    void forwardsCancellationAndPreservesAnEngineNotFoundResponse() throws Exception {
        authenticatedMvc.perform(post("/api/v1/task-compilations/{requestId}/actions/cancel", REQUEST_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("CANCEL_REQUESTED"));
        assertThat(enginePath.get()).isEqualTo(
                "/api/v1/task-compilations/" + REQUEST_ID + "/actions/cancel");

        engineStatus.set(404);
        authenticatedMvc.perform(post("/api/v1/task-compilations/{requestId}/actions/cancel", REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("engine detail 404"));
    }

    @Test
    void mapsCapacityTimeoutAuthenticationAndServerFailuresToSafeProblems() throws Exception {
        for (int upstreamStatus : List.of(400, 409, 429, 504)) {
            engineStatus.set(upstreamStatus);
            authenticatedMvc.perform(post("/api/v1/task-compilations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(compilationRequest()))
                    .andExpect(status().is(upstreamStatus))
                    .andExpect(jsonPath("$.detail").value("engine detail " + upstreamStatus));
        }
        engineStatus.set(401);
        authenticatedMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Task Engine 认证失败"));
        engineStatus.set(500);
        authenticatedMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Task Engine 返回异常响应"));
    }

    @Test
    void requiresAuthenticationAndTaskViewPermission() throws Exception {
        rawMvc.perform(post("/api/v1/task-compilations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        rawMvc.perform(post("/api/v1/task-compilations")
                        .header("Authorization", "Bearer " + tokenWithoutTaskView())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilationRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private void handleEngineRequest(HttpExchange exchange) throws IOException {
        engineAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        enginePath.set(exchange.getRequestURI().getPath());
        engineRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        int statusCode = engineStatus.get();
        String body;
        if (statusCode >= 400) {
            body = "{\"status\":" + statusCode + ",\"detail\":\"engine detail " + statusCode + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        } else if (exchange.getRequestURI().getPath().endsWith("/actions/cancel")) {
            body = "{\"requestId\":\"" + REQUEST_ID + "\",\"state\":\"CANCEL_REQUESTED\"}";
            statusCode = 202;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        } else {
            body = compilationResponse();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String tokenWithoutTaskView() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("data-scalpel-admin-test")
                .subject("limited-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("limited"))
                .claim("permissions", List.of())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static String compilationRequest() {
        return """
                {
                  "requestId":"%s",
                  "task":{"type":"CANVAS","definition":{"schemaVersion":1,"schemaMinorVersion":1,"nodes":[
                    {"id":"4add70a7-4948-42a5-af66-e56dbaccad3e","type":"JDBC_INPUT","name":"订单输入",
                     "layout":{"x":10,"y":10,"width":240,"height":120},
                     "configuration":{"dataSourceId":"%s","tableName":"orders"}},
                    {"id":"c24d2459-aeaa-4de7-9562-dd078dcd864f","type":"JOIN","name":"订单关联",
                     "layout":{"x":350,"y":10,"width":240,"height":120},
                     "configuration":{"leftTableName":"orders","rightTableName":"customers","outputTableName":"joined",
                     "joinType":"INNER","conditions":[{"leftColumnName":"customer_id","operator":"EQUALS","rightColumnName":"id"}]}},
                    {"id":"8fd542d5-37c2-4769-8ea6-47dff963073a","type":"JDBC_OUTPUT","name":"结果输出",
                     "layout":{"x":700,"y":10,"width":240,"height":120},
                     "configuration":{"sourceTableName":"joined","dataSourceId":"%s","targetTableName":"result",
                     "writeMode":"APPEND","columnMappingMode":"BY_NAME","columnMappings":[]}}
                  ],"edges":[]}},
                  "metadataSnapshot":{"dataSources":[{"id":"%s","enabled":true,"connectionKind":"JDBC",
                    "purposes":["SOURCE","DISTRIBUTION"],"tables":[{"tableName":"orders","objectType":"TABLE","columns":[
                      {"name":"order_id","fieldType":"LONG","length":null,"precision":null,"scale":null,"nullable":false,
                       "defaultValue":null,"autoIncrement":false,"generated":false,"comment":"订单ID"}
                    ]}]}],"models":[]}
                }
                """.formatted(REQUEST_ID, DATA_SOURCE_ID, DATA_SOURCE_ID, DATA_SOURCE_ID);
    }

    private static String compilationResponse() {
        return """
                {
                  "requestId":"%s","taskType":"CANVAS","valid":false,"durationMs":8,
                  "sparkApplicationId":"local-test",
                  "canvasIssues":[{"code":"INVALID_NODE_DEGREE","severity":"ERROR","message":"节点度数错误","nodeId":null,"path":"nodes"}],
                  "nodeResults":[{"nodeId":"4add70a7-4948-42a5-af66-e56dbaccad3e","state":"ERROR","inputTables":[],
                    "outputTables":[{"name":"orders","origin":{"kind":"JDBC","dataSourceId":"%s","tableName":"orders"},"columns":[
                      {"name":"order_id","fieldType":"LONG","length":null,"precision":null,"scale":null,"nullable":false,
                       "defaultValue":null,"autoIncrement":false,"generated":false,"comment":"订单ID"}
                    ]}],"issues":[]}]}
                """.formatted(REQUEST_ID, DATA_SOURCE_ID);
    }
}
