package cn.superhuang.data.scalpel.business.task.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SdkApiClientTest {
    @Test
    void relaysCurrentDocumentAndMapsMissingDocumentWithoutLeakingInternalDetails() throws Exception {
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/spark-jar-sdk-api", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("Bearer sdk-test", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = (status.get() == 200 ? "{\"version\":\"test\",\"fingerprint\":\"fresh\",\"types\":[]}"
                    : "{\"detail\":\"private server details\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new TaskEngineClient(new TaskEngineProperties("sdk-test", Duration.ofSeconds(2), Duration.ofSeconds(3)), new ObjectMapper());
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals("fresh", client.sdkApi(url).fingerprint());
            for (int remoteStatus : new int[]{404, 503, 401}) {
                status.set(remoteStatus);
                var error = assertThrows(ResponseStatusException.class, () -> client.sdkApi(url));
                assertEquals(502, error.getStatusCode().value());
                assertFalse(error.getReason().contains("private"));
            }
        } finally { server.stop(0); }
    }
}
