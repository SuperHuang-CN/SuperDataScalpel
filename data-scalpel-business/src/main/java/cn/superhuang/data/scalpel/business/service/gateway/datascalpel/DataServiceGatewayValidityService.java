package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.ApiConsumerCredentialRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.web.request.GatewayAccessValidityRequest;
import cn.superhuang.data.scalpel.business.service.gateway.web.response.GatewayAccessValidityResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class DataServiceGatewayValidityService {
    private final SuperApiGatewayAdminClient client;
    private final ServiceGatewayProperties properties;
    private final ApiConsumerCredentialRepository keys;
    private final ApiServiceSubscriptionRepository subscriptions;
    public DataServiceGatewayValidityService(SuperApiGatewayAdminClient client, ServiceGatewayProperties properties,
            ApiConsumerCredentialRepository keys, ApiServiceSubscriptionRepository subscriptions) {
        this.client = client; this.properties = properties; this.keys = keys; this.subscriptions = subscriptions;
    }
    public GatewayAccessValidityResponse access(String kind, UUID id, GatewayAccessValidityRequest update) {
        if (properties.provider() != GatewayProvider.DATASCALPEL)
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "当前网关未适配有效期功能，未执行变更");
        if (update != null && update.validFrom() != null && update.expiresAt() != null && !update.validFrom().isBefore(update.expiresAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生效时间必须早于到期时间");
        try {
            UUID remoteId;
            if ("keys".equals(kind)) {
                if (update != null && update.requestsPerSecond() != 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证不支持订阅限流");
                var key = keys.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "凭证不存在"));
                var consumer = client.findConsumer(key.getConsumerId().toString()).orElseThrow(() -> notSynchronized());
                var remoteKey = client.findApiKey(consumer.id(), id.toString()).orElseThrow(() -> notSynchronized());
                checkOwner(remoteKey.source(), remoteKey.externalId(), id);
                remoteId = remoteKey.id();
            } else if ("subscriptions".equals(kind)) {
                if (!subscriptions.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在");
                var subscription = client.findSubscription(id.toString()).orElseThrow(() -> notSynchronized());
                checkOwner(subscription.source(), subscription.externalId(), id);
                remoteId = subscription.id();
            } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型只支持 keys/subscriptions");
            return update == null ? client.getValidity(kind, remoteId) : client.updateValidity(kind, remoteId, update);
        } catch (SuperApiGatewayAdminException e) {
            if (e.status() == 400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "网关拒绝有效期配置，请检查时间范围与每秒请求数", e);
            if (e.isConflict() || e.isNotFound()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "网关对象不存在或归属已改变，请核对同步状态", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网关有效期操作未确认，请刷新核对状态或检查网关连接", e);
        }
    }
    private static ResponseStatusException notSynchronized() { return new ResponseStatusException(HttpStatus.CONFLICT, "对象尚未同步到自研网关"); }
    private static void checkOwner(String source, String externalId, UUID id) {
        if (!SuperApiGatewayAdminClient.SOURCE.equals(source) || !id.toString().equals(externalId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "网关对象归属不匹配");
    }
}
