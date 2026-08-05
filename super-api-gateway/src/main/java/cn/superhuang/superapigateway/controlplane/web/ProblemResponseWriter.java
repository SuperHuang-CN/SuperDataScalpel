package cn.superhuang.superapigateway.controlplane.web;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String title,
            String detail
    ) {
        try {
            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("type", URI.create("urn:super-api-gateway:problem:" + code.toLowerCase()));
            problem.put("title", title);
            problem.put("status", status.value());
            problem.put("detail", detail);
            problem.put("instance", exchange.getRequest().getPath().value());
            problem.put("code", code);
            problem.put("timestamp", Instant.now());
            byte[] bytes = objectMapper.writeValueAsBytes(problem);
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            exchange.getResponse().getHeaders().setContentLength(bytes.length);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
            );
        } catch (Exception exception) {
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        }
    }
}
