package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class GatewaySubscriptionPortRegistry {

    private final ServiceGatewayProperties properties;
    private final Map<GatewayProvider, GatewaySubscriptionPort> ports;

    public GatewaySubscriptionPortRegistry(
            ServiceGatewayProperties properties,
            List<GatewaySubscriptionPort> availablePorts
    ) {
        this.properties = properties;
        EnumMap<GatewayProvider, GatewaySubscriptionPort> registered = new EnumMap<>(GatewayProvider.class);
        for (GatewaySubscriptionPort port : availablePorts) {
            if (registered.put(port.provider(), port) != null) {
                throw new IllegalStateException("网关订阅适配器重复：" + port.provider());
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

    public GatewaySubscriptionPort require(GatewayProvider provider) {
        GatewaySubscriptionPort port = ports.get(provider);
        if (port == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前版本未提供 " + provider + " 订阅适配器"
            );
        }
        return port;
    }
}
