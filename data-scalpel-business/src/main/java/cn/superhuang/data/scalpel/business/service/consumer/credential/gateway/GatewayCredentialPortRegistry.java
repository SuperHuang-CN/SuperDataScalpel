package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class GatewayCredentialPortRegistry {

    private final ServiceGatewayProperties properties;
    private final Map<GatewayProvider, GatewayCredentialPort> ports;

    public GatewayCredentialPortRegistry(
            ServiceGatewayProperties properties,
            List<GatewayCredentialPort> availablePorts
    ) {
        this.properties = properties;
        EnumMap<GatewayProvider, GatewayCredentialPort> registered = new EnumMap<>(GatewayProvider.class);
        for (GatewayCredentialPort port : availablePorts) {
            if (registered.put(port.provider(), port) != null) {
                throw new IllegalStateException("网关凭证适配器重复：" + port.provider());
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

    public GatewayCredentialPort require(GatewayProvider provider) {
        GatewayCredentialPort port = ports.get(provider);
        if (port == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前版本未提供 " + provider + " 凭证适配器"
            );
        }
        return port;
    }
}
