package cn.superhuang.superapigateway.controlplane.service;

import cn.superhuang.superapigateway.controlplane.repository.GatewayApiKeyRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewaySubscriptionRepository;
import cn.superhuang.superapigateway.controlplane.web.request.AccessValidityRequest;
import cn.superhuang.superapigateway.controlplane.web.response.AccessValidityResponse;
import cn.superhuang.superapigateway.runtime.GatewayRuntimeHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccessValidityService {
    private final GatewayApiKeyRepository keys;
    private final GatewaySubscriptionRepository subscriptions;
    private final ConfigurationRevisionService revisions;
    private final GatewayRuntimeHolder runtime;
    public AccessValidityService(GatewayApiKeyRepository keys, GatewaySubscriptionRepository subscriptions,
                                 ConfigurationRevisionService revisions, GatewayRuntimeHolder runtime) {
        this.keys = keys; this.subscriptions = subscriptions; this.revisions = revisions; this.runtime = runtime;
    }
    @Transactional(readOnly = true)
    public AccessValidityResponse get(String kind, UUID id) {
        if ("keys".equals(kind)) {
            var key = keys.findById(id).orElseThrow(() -> new ResourceNotFoundException("凭证不存在"));
            return response(key.getValidFrom(), key.getExpiresAt(), 0, key.getStatus().name());
        }
        if (!"subscriptions".equals(kind)) throw new InvalidManagementRequestException("类型只支持 keys/subscriptions");
        var subscription = subscriptions.findById(id).orElseThrow(() -> new ResourceNotFoundException("订阅不存在"));
        return response(subscription.getValidFrom(), subscription.getExpiresAt(), subscription.getRequestsPerSecond(), subscription.getStatus().name());
    }
    @Transactional
    public AccessValidityResponse update(String kind, UUID id, AccessValidityRequest request, boolean machine) {
        if (request.validFrom() != null && request.expiresAt() != null && !request.validFrom().isBefore(request.expiresAt()))
            throw new InvalidManagementRequestException("生效时间必须早于到期时间");
        if ("keys".equals(kind)) {
            if (request.requestsPerSecond() != 0) throw new InvalidManagementRequestException("凭证不能设置订阅限流");
            var key = keys.findById(id).orElseThrow(() -> new ResourceNotFoundException("凭证不存在"));
            checkOwner(key.getSource(), machine); key.setValidity(request.validFrom(), request.expiresAt());
        } else if ("subscriptions".equals(kind)) {
            var subscription = subscriptions.findById(id).orElseThrow(() -> new ResourceNotFoundException("订阅不存在"));
            checkOwner(subscription.getSource(), machine);
            subscription.setValidity(request.validFrom(), request.expiresAt(), request.requestsPerSecond());
        } else throw new InvalidManagementRequestException("类型只支持 keys/subscriptions");
        revisions.bump(); return get(kind, id);
    }
    private static void checkOwner(String source, boolean machine) {
        if ("DATASCALPEL".equalsIgnoreCase(source) && !machine) throw new ResourceConflictException("托管对象请在 DataScalpel Admin 中修改");
    }
    private AccessValidityResponse response(Instant from, Instant to, int rate, String status) {
        Instant now = Instant.now();
        String state = !"ACTIVE".equals(status) ? status : from != null && now.isBefore(from) ? "NOT_YET_VALID"
                : to != null && !now.isBefore(to) ? "EXPIRED" : "ACTIVE";
        return new AccessValidityResponse(from, to, rate, state, revisions.currentRevision(), runtime.snapshot().revision());
    }
}
