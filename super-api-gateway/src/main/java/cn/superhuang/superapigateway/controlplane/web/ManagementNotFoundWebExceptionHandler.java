package cn.superhuang.superapigateway.controlplane.web;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-3)
public class ManagementNotFoundWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ProblemResponseWriter problems;

    public ManagementNotFoundWebExceptionHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        String path = exchange.getRequest().getPath().value();
        if (isAdminPath(path)
                && failure instanceof ResponseStatusException statusFailure
                && statusFailure.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                && !exchange.getResponse().isCommitted()) {
            return problems.write(
                    exchange,
                    HttpStatus.NOT_FOUND,
                    "MANAGEMENT_ENDPOINT_NOT_FOUND",
                    "Management endpoint not found",
                    "No management endpoint matches this request"
            );
        }
        return Mono.error(failure);
    }

    private static boolean isAdminPath(String path) {
        return path.equals("/admin-api") || path.startsWith("/admin-api/");
    }
}
