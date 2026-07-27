package cn.superhuang.data.scalpel.business.service.consumer.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class GatewayConsumerPortRegistry {

    private final ServiceGatewayProperties properties;
    private final Map<GatewayProvider, GatewayConsumerPort> ports;

    public GatewayConsumerPortRegistry(
            ServiceGatewayProperties properties,
            List<GatewayConsumerPort> availablePorts
    ) {
        this.properties = properties;
        EnumMap<GatewayProvider, GatewayConsumerPort> registered = new EnumMap<>(GatewayProvider.class);
        for (GatewayConsumerPort port : availablePorts) {
            GatewayConsumerPort duplicate = registered.put(port.provider(), port);
            if (duplicate != null) {
                throw new IllegalStateException("网关消费者适配器重复：" + port.provider());
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

    public GatewayConsumerPort require(GatewayProvider provider) {
        GatewayConsumerPort port = ports.get(provider);
        if (port == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前版本未提供 " + provider + " 消费者适配器"
            );
        }
        return port;
    }
}
