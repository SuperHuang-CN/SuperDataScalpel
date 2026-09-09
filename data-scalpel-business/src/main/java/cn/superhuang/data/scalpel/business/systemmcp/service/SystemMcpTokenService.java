package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.domain.*;
import cn.superhuang.data.scalpel.business.systemmcp.repository.*;
import cn.superhuang.data.scalpel.business.systemmcp.security.SystemMcpAuthentication;
import cn.superhuang.data.scalpel.business.systemmcp.web.request.*;
import cn.superhuang.data.scalpel.business.systemmcp.web.response.*;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Service
public class SystemMcpTokenService {
    private final SystemMcpAccessTokenRepository tokens;
    private final SystemUserRepository users;
    private final SystemAccessService access;
    public SystemMcpTokenService(SystemMcpAccessTokenRepository t,SystemUserRepository u,SystemAccessService a) {
        tokens=t;
        users=u;
        access=a;
    }
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    @Transactional public SystemMcpAuthentication authenticate(String secret) {
        if(secret==null||!secret.startsWith("dssmcp_")||secret.length()>128)throw invalid();
        var t=tokens.findByTokenDigest(digest(secret)).orElseThrow(SystemMcpTokenService::invalid);
        if(!t.getEnabled()||t.getExpiresAt()!=null&&!t.getExpiresAt().isAfter(Instant.now()))throw invalid();
        var u=users.findById(t.getUserId()).filter(x->x.isEnabled()).orElseThrow(SystemMcpTokenService::invalid);
        var current=access.findAuthenticationUser(u.getUsername()).orElseThrow(SystemMcpTokenService::invalid);
        List<SimpleGrantedAuthority> authorities=new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_"+current.roleCode()));
        current.permissionCodes().forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
        if(tokens.touch(t.getId(),t.getTokenDigest(),Instant.now())!=1)throw invalid();
        return new SystemMcpAuthentication(current.username(),t.getId(),secret,authorities);
    }
    private static BadCredentialsException invalid() {
        return new BadCredentialsException("系统 MCP 令牌无效或绑定用户不可用");
    }
    public SystemMcpAccessToken get(UUID id) {
        return tokens.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"令牌不存在"));
    }
    public SystemMcpTokenResponse response(SystemMcpAccessToken t) {
        return new SystemMcpTokenResponse(t.getId(),t.getName(),t.getUserId(),users.findById(t.getUserId()).map(u->u.getUsername()).orElse("已删除用户"),t.getEnabled(),t.getRevision(),t.getExpiresAt(),t.getLastUsedAt(),t.getCreatedAt());
    }
    public List<SystemMcpTokenResponse> responses(List<SystemMcpAccessToken> values) {
        Map<UUID,String> names=new HashMap<>();
        users.findAllById(values.stream().map(SystemMcpAccessToken::getUserId).distinct().toList()).forEach(u->names.put(u.getId(),u.getUsername()));
        return values.stream().map(t->new SystemMcpTokenResponse(t.getId(),t.getName(),t.getUserId(),names.getOrDefault(t.getUserId(),"已删除用户"),t.getEnabled(),t.getRevision(),t.getExpiresAt(),t.getLastUsedAt(),t.getCreatedAt())).toList();
    }
    @Transactional public SystemMcpIssuedTokenResponse create(CreateSystemMcpTokenRequest r) {
        if(!users.existsById(r.userId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"绑定用户不存在");
        validateExpiry(r.expiresAt());
        var t=new SystemMcpAccessToken();
        t.setName(r.name());
        t.setUserId(r.userId());
        t.setEnabled(true);
        t.setExpiresAt(r.expiresAt());
        return issue(t);
    }
    @Transactional public SystemMcpIssuedTokenResponse rotate(UUID id) {
        return issue(get(id));
    }
    private SystemMcpIssuedTokenResponse issue(SystemMcpAccessToken t) {
        byte[] bytes=new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret="dssmcp_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        t.setTokenDigest(digest(secret));
        t.setRevision(t.getRevision()+1);
        tokens.saveAndFlush(t);
        return new SystemMcpIssuedTokenResponse(response(t),secret);
    }
    @Transactional public SystemMcpTokenResponse update(UUID id,UpdateSystemMcpTokenRequest r) {
        validateExpiry(r.expiresAt());
        var t=get(id);
        t.setName(r.name());
        t.setExpiresAt(r.expiresAt());
        return response(t);
    }
    @Transactional public void enabled(UUID id,boolean value) {
        get(id).setEnabled(value);
    }
    @Transactional public void delete(UUID id) {
        tokens.delete(get(id));
    }
    private void validateExpiry(Instant expiry) {
        if(expiry!=null&&!expiry.isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"过期时间必须晚于当前时间");
    }
}
