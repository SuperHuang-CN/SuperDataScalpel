package cn.superhuang.superapigateway.dataplane;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayDispatchRouteConfiguration {

    @Bean
    RouteLocator gatewayDispatchRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("super-api-gateway-dispatch", route -> route
                        .predicate(exchange -> !reserved(exchange.getRequest().getPath().value()))
                        .uri("http://127.0.0.1:9"))
                .build();
    }

    private static boolean reserved(String path) {
        return path.equals("/admin-api")
                || path.startsWith("/admin-api/")
                || path.equals("/actuator")
                || path.startsWith("/actuator/");
    }
}
