package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.web.request.GatewayTrafficPolicyRequest;
import cn.superhuang.data.scalpel.business.service.gateway.web.response.GatewayTrafficPolicyResponse;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class DataServiceGatewayPolicyService {
    private final SuperApiGatewayAdminClient client;
    private final ServiceGatewayProperties properties;
    private final DataServiceRepository services;
    public DataServiceGatewayPolicyService(SuperApiGatewayAdminClient client, ServiceGatewayProperties properties,
                                         DataServiceRepository services) {
        this.client = client; this.properties = properties; this.services = services;
    }
    public GatewayTrafficPolicyResponse get(UUID id) {
        return remote(() -> client.getTrafficPolicy(resolve(id)));
    }
    public GatewayTrafficPolicyResponse update(UUID id, GatewayTrafficPolicyRequest request) {
        return remote(() -> client.updateTrafficPolicy(resolve(id), request));
    }
    private UUID resolve(UUID id) {
        if (!services.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在");
        if (properties.provider() != GatewayProvider.DATASCALPEL)
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "当前网关不支持此保护策略，未执行任何变更");
        var remote = client.findService(id.toString()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "请先将服务发布到自研网关"));
        if (!SuperApiGatewayAdminClient.SOURCE.equals(remote.source()) || !id.toString().equals(remote.externalId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "网关服务归属不匹配，禁止修改");
        return remote.id();
    }
    private <T> T remote(Supplier<T> action) {
        try { return action.get(); }
        catch (SuperApiGatewayAdminException e) {
            if (e.status() == 400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "网关拒绝保护策略：请检查数值范围及 IP/CIDR 格式", e);
            if (e.isConflict() || e.isNotFound()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "网关对象不存在或归属已改变，请先核对服务同步状态", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "网关保护策略读取或保存未确认，请检查网关连接并刷新核对；不要假定已生效", e);
        }
    }
}
