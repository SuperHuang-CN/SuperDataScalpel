package cn.superhuang.data.scalpel.business.service.gateway.service;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class GatewayServicePortRegistry {

    private final ServiceGatewayProperties properties;
    private final Map<GatewayProvider, GatewayServicePort> ports;

    public GatewayServicePortRegistry(
            ServiceGatewayProperties properties,
            List<GatewayServicePort> availablePorts
    ) {
        this.properties = properties;
        EnumMap<GatewayProvider, GatewayServicePort> registered = new EnumMap<>(GatewayProvider.class);
        for (GatewayServicePort port : availablePorts) {
            GatewayServicePort duplicate = registered.put(port.provider(), port);
            if (duplicate != null) {
                throw new IllegalStateException("网关服务适配器重复：" + port.provider());
            }
        }
        this.ports = Map.copyOf(registered);
    }

    public GatewayProvider activeProvider() {
        GatewayProvider provider = properties.provider();
        if (provider == GatewayProvider.NONE) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "数据服务网关尚未配置");
        }
        require(provider);
        return provider;
    }

    public GatewayServicePort require(GatewayProvider provider) {
        GatewayServicePort port = ports.get(provider);
        if (port == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前版本未提供 " + provider + " 服务适配器"
            );
        }
        return port;
    }
}
