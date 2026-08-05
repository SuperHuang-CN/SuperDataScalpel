package cn.superhuang.superapigateway.dataplane;

import cn.superhuang.superapigateway.controlplane.web.ProblemResponseWriter;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayProxyErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ProblemResponseWriter problems;

    public GatewayProxyErrorWebExceptionHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (!Boolean.TRUE.equals(
                exchange.getAttribute(GatewayDispatchFilter.PROXY_REQUEST_ATTRIBUTE)
        )) {
            return Mono.error(failure);
        }
        if (exchange.getResponse().isCommitted()) return Mono.error(failure);
        Throwable cause = rootCause(failure);
        boolean timeout = cause instanceof TimeoutException
                || cause.getClass().getSimpleName().contains("Timeout");
        return problems.write(
                exchange,
                timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                timeout ? "GATEWAY_UPSTREAM_TIMEOUT" : "GATEWAY_UPSTREAM_UNAVAILABLE",
                timeout ? "Upstream timeout" : "Upstream unavailable",
                timeout
                        ? "The upstream service did not respond before the configured timeout"
                        : "The gateway could not complete the upstream request"
        );
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
