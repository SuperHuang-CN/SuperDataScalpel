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
        return new SystemMcpTokenResponse(t.getId(),t.getName(),t.getUserId(),users.findById(t.getUserId()).map(u->u.getUsername()).orElse("已删除用户"),t.getEnabled(),t.getRevision(),t.getExpiresAt(),t.getLastUsedAt(),t.getCreatedAt(),t.isManaged());
    }
    public List<SystemMcpTokenResponse> responses(List<SystemMcpAccessToken> values) {
        Map<UUID,String> names=new HashMap<>();
        users.findAllById(values.stream().map(SystemMcpAccessToken::getUserId).distinct().toList()).forEach(u->names.put(u.getId(),u.getUsername()));
        return values.stream().map(t->new SystemMcpTokenResponse(t.getId(),t.getName(),t.getUserId(),names.getOrDefault(t.getUserId(),"已删除用户"),t.getEnabled(),t.getRevision(),t.getExpiresAt(),t.getLastUsedAt(),t.getCreatedAt(),t.isManaged())).toList();
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
        return issue(manual(id));
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
        var t=manual(id);
        t.setName(r.name());
        t.setExpiresAt(r.expiresAt());
        return response(t);
    }
    @Transactional public void enabled(UUID id,boolean value) {
        manual(id).setEnabled(value);
    }
    @Transactional public void delete(UUID id) {
        tokens.delete(manual(id));
    }
    private SystemMcpAccessToken manual(UUID id) {
        var token=get(id);
        if(token.isManaged()) {
            throw new cn.superhuang.data.scalpel.web.error.CodedProblemException(HttpStatus.CONFLICT,"SYSTEM_MCP_TOKEN_MANAGED","DSH 系统托管令牌不允许手工修改");
        }
        return token;
    }
    /** Internal provisioning only; no public management resource exposes this operation. */
    @Transactional public SystemMcpIssuedTokenResponse createManaged(UUID userId) {
        users.findById(userId).filter(u->u.isEnabled()).orElseThrow(SystemMcpTokenService::invalid);
        var token=new SystemMcpAccessToken(); token.setName("DSH:"+userId); token.setUserId(userId);
        token.setManaged(true); token.setEnabled(true); return issue(token);
    }
    public record ManagedStateChange(UUID tokenId,String username,boolean enabled) {}
    @Transactional public List<ManagedStateChange> reconcileManagedStates() {
        var managed=tokens.findByManagedTrue();
        Map<UUID,cn.superhuang.data.scalpel.business.system.access.domain.SystemUser> current=new HashMap<>();
        users.findAllById(managed.stream().map(SystemMcpAccessToken::getUserId).distinct().toList()).forEach(user->current.put(user.getId(),user));
        List<ManagedStateChange> changes=new ArrayList<>();
        for(var token:managed) {
            var user=current.get(token.getUserId());boolean enabled=user!=null&&user.isEnabled();
            if(token.getEnabled()!=enabled) {
                token.setEnabled(enabled);
                changes.add(new ManagedStateChange(token.getId(),user==null?token.getUserId().toString():user.getUsername(),enabled));
            }
        }
        return changes;
    }
    private void validateExpiry(Instant expiry) {
        if(expiry!=null&&!expiry.isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"过期时间必须晚于当前时间");
    }
}
