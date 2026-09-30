package cn.superhuang.superapigateway.controlplane.service;

import cn.superhuang.superapigateway.controlplane.repository.GatewayServiceRepository;
import cn.superhuang.superapigateway.controlplane.web.request.TrafficPolicyRequest;
import cn.superhuang.superapigateway.controlplane.web.response.TrafficPolicyResponse;
import cn.superhuang.superapigateway.runtime.GatewayRuntimeHolder;
import cn.superhuang.superapigateway.runtime.RuntimeTrafficPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Service
public class GatewayTrafficPolicyService {
    private final GatewayServiceRepository services;
    private final ConfigurationRevisionService revisions;
    private final GatewayRuntimeHolder runtime;
    private final ObjectMapper mapper;
    public GatewayTrafficPolicyService(GatewayServiceRepository services, ConfigurationRevisionService revisions,
                                       GatewayRuntimeHolder runtime, ObjectMapper mapper) {
        this.services = services; this.revisions = revisions; this.runtime = runtime; this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public TrafficPolicyResponse get(UUID id) {
        var service = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("服务不存在"));
        var policy = service.getTrafficPolicy() == null ? TrafficPolicyRequest.unrestricted()
                : mapper.readValue(service.getTrafficPolicy(), TrafficPolicyRequest.class);
        return new TrafficPolicyResponse(policy, revisions.currentRevision(), runtime.snapshot().revision(), "NODE");
    }
    @Transactional
    public TrafficPolicyResponse update(UUID id, TrafficPolicyRequest request, boolean machine) {
        var service = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("服务不存在"));
        if ("DATASCALPEL".equalsIgnoreCase(service.getSource()) && !machine)
            throw new ResourceConflictException("该服务由 DataScalpel 管理，请在 Admin 端修改保护策略");
        try { RuntimeTrafficPolicy.compile(request); }
        catch (IllegalArgumentException e) { throw new InvalidManagementRequestException(e.getMessage()); }
        service.setTrafficPolicy(mapper.writeValueAsString(request));
        revisions.bump();
        return get(id);
    }
}
