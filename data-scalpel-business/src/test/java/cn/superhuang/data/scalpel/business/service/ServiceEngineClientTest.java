package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceEngineClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesEachEnginesDecryptedTokenAndCandidatePlaintextToken() throws IOException {
        CopyOnWriteArrayList<String> authorizationHeaders = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/info", exchange -> respond(exchange, authorizationHeaders));
        server.start();

        ServiceEngineCredentialCipher cipher = new ServiceEngineCredentialCipher(
                new ServiceEngineSecurityProperties(Base64.getEncoder().encodeToString(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
                ))
        );
        ServiceEngineClient client = new ServiceEngineClient(cipher);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ServiceEngine first = ServiceEngine.create(
                "engine_a", "Engine A", baseUrl, baseUrl,
                cipher.encrypt("token-a"), true, null
        );
        ServiceEngine second = ServiceEngine.create(
                "engine_b", "Engine B", baseUrl, baseUrl,
                cipher.encrypt("token-b"), true, null
        );

        ServiceEngineInfoResponse firstResponse = client.info(first);
        ServiceEngineInfoResponse secondResponse = client.info(second);
        ServiceEngineInfoResponse candidateResponse = client.info(baseUrl, "candidate-token");

        assertEquals("reported_engine", firstResponse.code());
        assertEquals("reported_engine", secondResponse.code());
        assertEquals("reported_engine", candidateResponse.code());
        assertEquals(
                java.util.List.of("Bearer token-a", "Bearer token-b", "Bearer candidate-token"),
                authorizationHeaders
        );
    }

    private static void respond(HttpExchange exchange, CopyOnWriteArrayList<String> authorizationHeaders)
            throws IOException {
        authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "{\"code\":\"reported_engine\",\"databaseTypes\":[\"POSTGRESQL\"]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
