package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineAccessPolicy;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineAccessPolicyStatus;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineAccessPolicyRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateServiceEngineAccessPolicyRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineAccessPolicyResponse;
import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyRequest;
import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Coordinates the desired source-address policy with a remote Service Engine outside DB transactions. */
@Service
public class ServiceEngineAccessPolicyService {

    private static final Pattern ADDRESS_TEXT = Pattern.compile("[0-9a-fA-F:.]+");

    private final ServiceEngineAccessPolicyRepository repository;
    private final ServiceEngineRepository engineRepository;
    private final ServiceEngineClient engineClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ServiceEngineAccessPolicyService(
            ServiceEngineAccessPolicyRepository repository,
            ServiceEngineRepository engineRepository,
            ServiceEngineClient engineClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.engineRepository = engineRepository;
        this.engineClient = engineClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public ServiceEngineAccessPolicyResponse get(UUID engineId) {
        requireDataScalpelEngine(engineId);
        return repository.findByEngineId(engineId)
                .map(this::response)
                .orElseGet(() -> new ServiceEngineAccessPolicyResponse(
                        engineId, List.of(), List.of(), 0, 0,
                        ServiceEngineAccessPolicyStatus.NOT_CONFIGURED, null, null, null
                ));
    }

    public ServiceEngineAccessPolicyResponse update(
            UUID engineId,
            UpdateServiceEngineAccessPolicyRequest request
    ) {
        PolicyOperation operation = requireResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEnabledDataScalpelEngine(engineId);
            List<String> allowCidrs = normalizeRules(request.allowCidrs());
            if (allowCidrs.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访问白名单不能为空");
            List<String> denyCidrs = normalizeRules(request.denyCidrs());
            ServiceEngineAccessPolicy policy = repository.findByEngineId(engineId)
                    .orElseGet(() -> ServiceEngineAccessPolicy.pending(
                            engineId, write(allowCidrs), write(denyCidrs)
                    ));
            if (policy.getId() != null) policy.replace(write(allowCidrs), write(denyCidrs));
            repository.saveAndFlush(policy);
            return new PolicyOperation(engine, policy.getDesiredRevision(), allowCidrs, denyCidrs);
        }));
        return apply(operation);
    }

    public ServiceEngineAccessPolicyResponse sync(UUID engineId) {
        PolicyOperation operation = requireResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEnabledDataScalpelEngine(engineId);
            ServiceEngineAccessPolicy policy = repository.findByEngineId(engineId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎尚未配置访问策略"));
            List<String> allowCidrs = read(policy.getAllowCidrsJson());
            if (allowCidrs.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "访问白名单不能为空");
            policy.beginSync();
            repository.saveAndFlush(policy);
            return new PolicyOperation(engine, policy.getDesiredRevision(), allowCidrs, read(policy.getDenyCidrsJson()));
        }));
        return apply(operation);
    }

    @Transactional(readOnly = true)
    public void requireReadyPolicy(UUID engineId) {
        ServiceEngineAccessPolicy policy = repository.findByEngineId(engineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎尚未配置访问白名单"));
        if (policy.getStatus() != ServiceEngineAccessPolicyStatus.READY || policy.getAppliedRevision() != policy.getDesiredRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎访问策略尚未就绪，请先成功同步白名单");
        }
    }

    @Transactional
    public void markOutdated(UUID engineId) {
        repository.findByEngineId(engineId).ifPresent(policy -> {
            policy.markOutdated();
            repository.flush();
        });
    }

    @Transactional
    public void deleteByEngineId(UUID engineId) {
        repository.deleteByEngineId(engineId);
    }

    private ServiceEngineAccessPolicyResponse apply(PolicyOperation operation) {
        String failure = null;
        try {
            EngineAccessPolicyApplyResponse response = engineClient.applyAccessPolicy(operation.engine(),
                    new EngineAccessPolicyApplyRequest(
                            operation.engine().getCode(), operation.revision(),
                            operation.allowCidrs(), operation.denyCidrs()
                    ));
            if (response == null || response.revision() != operation.revision() || !"READY".equals(response.status())) {
                throw new IllegalStateException("Service Engine 未确认访问策略版本");
            }
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        ServiceEngineAccessPolicyResponse result = requireResult(transactionTemplate.execute(status -> {
            ServiceEngineAccessPolicy policy = repository.findByEngineId(operation.engine().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎访问策略不存在"));
            if (policy.getDesiredRevision() == operation.revision()) {
                if (finalFailure == null) policy.ready(operation.revision());
                else policy.failed(finalFailure);
            }
            repository.saveAndFlush(policy);
            return response(policy);
        }));
        if (failure != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务引擎访问策略同步失败：" + failure);
        }
        return result;
    }

    private ServiceEngineAccessPolicyResponse response(ServiceEngineAccessPolicy policy) {
        return new ServiceEngineAccessPolicyResponse(
                policy.getEngineId(), read(policy.getAllowCidrsJson()), read(policy.getDenyCidrsJson()),
                policy.getDesiredRevision(), policy.getAppliedRevision(), policy.getStatus(),
                policy.getLastError(), policy.getAppliedAt(), policy.getUpdatedAt()
        );
    }

    private ServiceEngine requireEngine(UUID id) {
        return engineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务引擎不存在"));
    }

    private ServiceEngine requireDataScalpelEngine(UUID id) {
        ServiceEngine engine = requireEngine(id);
        if (engine.getType() != ServiceEngineType.DATASCALPEL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GeoServer 空间引擎不使用 Service Engine 访问策略");
        }
        return engine;
    }

    private ServiceEngine requireEnabledDataScalpelEngine(UUID id) {
        ServiceEngine engine = requireDataScalpelEngine(id);
        if (!engine.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        return engine;
    }

    private List<String> normalizeRules(List<String> rules) {
        if (rules == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String rule : rules) result.add(normalizeRule(rule));
        return List.copyOf(result);
    }

    private String normalizeRule(String raw) {
        if (raw == null || raw.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP 或 CIDR 不能为空");
        String value = raw.trim();
        int slash = value.indexOf('/');
        if (slash != value.lastIndexOf('/')) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIDR 格式无效：" + value);
        String addressPart = slash < 0 ? value : value.substring(0, slash);
        if (!ADDRESS_TEXT.matcher(addressPart).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIDR 必须使用 IP 地址：" + value);
        byte[] address;
        try {
            address = normalizedAddress(InetAddress.getByName(addressPart).getAddress());
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIDR 格式无效：" + value, exception);
        }
        int width = address.length * Byte.SIZE;
        int prefix = slash < 0 ? width : parsePrefix(value.substring(slash + 1), width, value);
        mask(address, prefix);
        try {
            return InetAddress.getByAddress(address).getHostAddress() + "/" + prefix;
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIDR 格式无效：" + value, exception);
        }
    }

    private static int parsePrefix(String text, int width, String source) {
        try {
            int prefix = Integer.parseInt(text);
            if (prefix < 0 || prefix > width) throw new NumberFormatException();
            return prefix;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIDR 前缀长度无效：" + source);
        }
    }

    private static void mask(byte[] address, int prefix) {
        int whole = prefix / Byte.SIZE;
        int remainder = prefix % Byte.SIZE;
        if (remainder > 0 && whole < address.length) {
            address[whole] &= (byte) (0xFF << (Byte.SIZE - remainder));
            whole++;
        }
        Arrays.fill(address, whole, address.length, (byte) 0);
    }

    private static byte[] normalizedAddress(byte[] address) {
        if (address.length != 16) return address;
        for (int index = 0; index < 10; index++) if (address[index] != 0) return address;
        if (address[10] != (byte) 0xFF || address[11] != (byte) 0xFF) return address;
        return Arrays.copyOfRange(address, 12, 16);
    }

    private List<String> read(String json) {
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("访问策略内容无法读取", exception);
        }
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("访问策略内容无法保存", exception);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "远程调用失败" : message.substring(0, Math.min(500, message.length()));
    }

    private static <T> T requireResult(T value) {
        if (value == null) throw new IllegalStateException("事务未返回访问策略处理结果");
        return value;
    }

    private record PolicyOperation(ServiceEngine engine, long revision, List<String> allowCidrs, List<String> denyCidrs) {
    }
}
