package cn.superhuang.data.scalpel.engine.accesspolicy;

import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyRequest;
import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyResponse;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Persists, restores and evaluates the Engine's source-address policy without per-request I/O. */
@Service
public class EngineAccessPolicyService {

    private static final Pattern ADDRESS_TEXT = Pattern.compile("[0-9a-fA-F:.]+");

    private final EngineAccessPolicyRepository repository;
    private final EngineProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<AccessPolicySnapshot> snapshot = new AtomicReference<>();

    public EngineAccessPolicyService(
            EngineAccessPolicyRepository repository,
            EngineProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restoreAppliedPolicy() {
        repository.findByEngineCode(properties.code()).ifPresent(policy -> {
            try {
                List<CidrRule> allowed = parseRules(read(policy.getAllowCidrsJson()));
                if (allowed.isEmpty()) return;
                snapshot.set(new AccessPolicySnapshot(allowed, parseRules(read(policy.getDenyCidrsJson()))));
            } catch (RuntimeException ignored) {
                // An unreadable policy fails closed instead of making the Engine available.
            }
        });
    }

    @Transactional
    public EngineAccessPolicyApplyResponse apply(EngineAccessPolicyApplyRequest request) {
        if (!properties.code().equals(request.engineCode().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访问策略的 Engine 编码与当前实例不一致");
        }
        if (request.revision() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访问策略版本必须大于 0");
        }
        List<CidrRule> allowed = parseRequestRules(request.allowCidrs());
        if (allowed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "访问白名单不能为空");
        }
        List<CidrRule> denied = parseRequestRules(request.denyCidrs());
        List<String> normalizedAllowed = allowed.stream().map(CidrRule::canonical).toList();
        List<String> normalizedDenied = denied.stream().map(CidrRule::canonical).toList();
        String policyHash = hash(normalizedAllowed, normalizedDenied);

        EngineAccessPolicy current = repository.findByEngineCode(properties.code()).orElse(null);
        if (current != null && request.revision() < current.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "访问策略版本落后于当前 Engine 配置");
        }
        if (current != null && request.revision() == current.getRevision()) {
            if (!current.getPolicyHash().equals(policyHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一访问策略版本的内容不一致");
            }
            return new EngineAccessPolicyApplyResponse(properties.code(), current.getRevision(), "READY");
        }

        String allowCidrsJson = write(normalizedAllowed);
        String denyCidrsJson = write(normalizedDenied);
        EngineAccessPolicy next = current == null
                ? new EngineAccessPolicy(properties.code(), request.revision(), policyHash, allowCidrsJson, denyCidrsJson)
                : current;
        if (current != null) {
            next.apply(request.revision(), policyHash, allowCidrsJson, denyCidrsJson);
        }
        repository.saveAndFlush(next);
        snapshot.set(new AccessPolicySnapshot(allowed, denied));
        return new EngineAccessPolicyApplyResponse(properties.code(), request.revision(), "READY");
    }

    public PolicyDecision evaluate(String remoteAddress) {
        AccessPolicySnapshot current = snapshot.get();
        if (current == null) {
            return new PolicyDecision(false, "来源 IP " + safeAddress(remoteAddress) + " 未命中当前 Service Engine 的访问白名单");
        }
        try {
            byte[] address = parseAddress(remoteAddress);
            if (current.denied().stream().anyMatch(rule -> rule.matches(address))) {
                return new PolicyDecision(false, "来源 IP " + safeAddress(remoteAddress) + " 已被当前 Service Engine 的访问黑名单拒绝");
            }
            if (current.allowed().stream().anyMatch(rule -> rule.matches(address))) {
                return new PolicyDecision(true, null);
            }
            return new PolicyDecision(false, "来源 IP " + safeAddress(remoteAddress) + " 未命中当前 Service Engine 的访问白名单");
        } catch (IllegalArgumentException exception) {
            return new PolicyDecision(false, "来源 IP " + safeAddress(remoteAddress) + " 无法识别，当前访问被拒绝");
        }
    }

    private List<CidrRule> parseRules(List<String> values) {
        if (values == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<CidrRule> result = new ArrayList<>();
        for (String value : values) {
            CidrRule rule = CidrRule.parse(value);
            if (seen.add(rule.canonical())) result.add(rule);
        }
        return List.copyOf(result);
    }

    private List<CidrRule> parseRequestRules(List<String> values) {
        try {
            return parseRules(values);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private List<String> read(String json) {
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("已保存的访问策略无法读取", exception);
        }
    }

    private String write(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("访问策略无法保存", exception);
        }
    }

    private static String hash(List<String> allowed, List<String> denied) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.join("\n", allowed).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(String.join("\n", denied).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static byte[] parseAddress(String text) {
        if (text == null || !ADDRESS_TEXT.matcher(text).matches()) {
            throw new IllegalArgumentException("invalid address");
        }
        try {
            return normalizedAddress(InetAddress.getByName(text).getAddress());
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("invalid address", exception);
        }
    }

    private static String safeAddress(String value) {
        return value == null || value.isBlank() ? "未知" : value.substring(0, Math.min(128, value.length()));
    }

    private record AccessPolicySnapshot(List<CidrRule> allowed, List<CidrRule> denied) {
    }

    public record PolicyDecision(boolean allowed, String denialDetail) {
    }

    private record CidrRule(byte[] network, int prefixLength, String canonical) {

        private static CidrRule parse(String raw) {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException("IP 或 CIDR 不能为空");
            String value = raw.trim();
            int slash = value.indexOf('/');
            if (slash != value.lastIndexOf('/')) throw new IllegalArgumentException("CIDR 格式无效：" + value);
            String addressText = slash < 0 ? value : value.substring(0, slash);
            byte[] address = parseAddress(addressText);
            int width = address.length * Byte.SIZE;
            int prefix = slash < 0 ? width : parsePrefix(value.substring(slash + 1), width, value);
            byte[] network = address.clone();
            mask(network, prefix);
            try {
                String canonical = InetAddress.getByAddress(network).getHostAddress().toLowerCase(Locale.ROOT)
                        + "/" + prefix;
                return new CidrRule(network, prefix, canonical);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("CIDR 格式无效：" + value, exception);
            }
        }

        private boolean matches(byte[] address) {
            if (address.length != network.length) return false;
            int completeBytes = prefixLength / Byte.SIZE;
            int remainder = prefixLength % Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (network[index] != address[index]) return false;
            }
            if (remainder == 0) return true;
            int mask = 0xFF << (Byte.SIZE - remainder);
            return (network[completeBytes] & mask) == (address[completeBytes] & mask);
        }

        private static int parsePrefix(String value, int width, String source) {
            try {
                int prefix = Integer.parseInt(value);
                if (prefix < 0 || prefix > width) throw new NumberFormatException();
                return prefix;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("CIDR 前缀长度无效：" + source);
            }
        }

        private static void mask(byte[] address, int prefix) {
            int wholeBytes = prefix / Byte.SIZE;
            int remainder = prefix % Byte.SIZE;
            if (remainder > 0 && wholeBytes < address.length) {
                address[wholeBytes] &= (byte) (0xFF << (Byte.SIZE - remainder));
                wholeBytes++;
            }
            Arrays.fill(address, wholeBytes, address.length, (byte) 0);
        }
    }

    private static byte[] normalizedAddress(byte[] value) {
        if (value.length != 16) return value;
        for (int index = 0; index < 10; index++) {
            if (value[index] != 0) return value;
        }
        if (value[10] != (byte) 0xFF || value[11] != (byte) 0xFF) return value;
        return Arrays.copyOfRange(value, 12, 16);
    }
}
