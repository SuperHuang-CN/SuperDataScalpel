package cn.superhuang.data.scalpel.business.datasource.service.http;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HttpApiConnectorRegistry {

    private final Map<String, HttpApiConnector> connectors;

    public HttpApiConnectorRegistry(List<HttpApiConnector> connectors) {
        this.connectors = connectors.stream().collect(Collectors.toUnmodifiableMap(
                HttpApiConnector::type, Function.identity()));
    }

    public HttpApiConnector require(String type) {
        HttpApiConnector connector = connectors.get(type);
        if (connector == null) {
            throw new HttpApiExecutionException("API_CONNECTOR_NOT_FOUND", "API 连接器未注册：" + type);
        }
        return connector;
    }
}
