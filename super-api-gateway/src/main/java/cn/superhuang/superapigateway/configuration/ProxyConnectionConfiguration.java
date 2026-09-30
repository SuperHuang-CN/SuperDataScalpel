package cn.superhuang.superapigateway.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientFactory;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.config.HttpClientSslConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.resources.ConnectionProvider;
import java.time.Duration;
import java.util.List;

@Configuration
public class ProxyConnectionConfiguration {
    @Bean(destroyMethod = "dispose")
    ConnectionProvider gatewayConnectionProvider(HttpClientProperties properties,
            @Value("${super-api-gateway.max-pending-connections:1000}") int pending) {
        var pool = properties.getPool();
        if (pending < 1 || pool.getMaxConnections() < 1 || pool.getAcquireTimeout() < 1)
            throw new IllegalArgumentException("网关连接数、排队数及等待超时必须大于零");
        return ConnectionProvider.builder("super-api-gateway-proxy")
                .maxConnections(pool.getMaxConnections()).pendingAcquireMaxCount(pending)
                .pendingAcquireTimeout(Duration.ofMillis(pool.getAcquireTimeout()))
                .maxIdleTime(pool.getMaxIdleTime()).maxLifeTime(pool.getMaxLifeTime())
                .evictInBackground(pool.getEvictionInterval()).metrics(true).build();
    }

    @Bean
    HttpClientFactory gatewayHttpClientFactory(HttpClientProperties properties, ServerProperties serverProperties,
            HttpClientSslConfigurer ssl, List<HttpClientCustomizer> customizers, ConnectionProvider gatewayConnectionProvider) {
        // SCG's fixed pool explicitly installs an unbounded pending queue. Replace only the pool,
        // retaining SCG's timeout, SSL, proxy and decoder configuration.
        return new HttpClientFactory(properties, serverProperties, ssl, customizers) {
            @Override protected ConnectionProvider buildConnectionProvider(HttpClientProperties ignored) {
                return gatewayConnectionProvider;
            }
        };
    }
}
