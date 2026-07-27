package cn.superhuang.datascalpel.taskengine.httpapi;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class HttpApiPullConnectorRegistry {

    private static final Map<String, HttpApiPullConnector> CONNECTORS = load();

    private HttpApiPullConnectorRegistry() {
    }

    public static HttpApiPullConnector require(String type) {
        HttpApiPullConnector connector = CONNECTORS.get(type);
        if (connector == null) {
            throw new HttpApiPullException("API_CONNECTOR_NOT_FOUND", "API 连接器未注册：" + type);
        }
        return connector;
    }

    private static Map<String, HttpApiPullConnector> load() {
        Map<String, HttpApiPullConnector> connectors = new LinkedHashMap<>();
        register(connectors, new GenericHttpApiPullConnector());
        ServiceLoader.load(HttpApiPullConnector.class).forEach(connector -> register(connectors, connector));
        if (!connectors.containsKey(HttpApiContracts.GENERIC_CONNECTOR)) {
            throw new IllegalStateException("GENERIC_HTTP connector is required");
        }
        return Map.copyOf(connectors);
    }

    private static void register(Map<String, HttpApiPullConnector> connectors, HttpApiPullConnector connector) {
        HttpApiPullConnector duplicate = connectors.putIfAbsent(connector.type(), connector);
        if (duplicate != null && duplicate.getClass() != connector.getClass()) {
            throw new IllegalStateException("Duplicate HTTP API connector: " + connector.type());
        }
    }
}
