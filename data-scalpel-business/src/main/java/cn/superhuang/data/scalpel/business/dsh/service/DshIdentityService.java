package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.security.DshLoginIdentity;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
@Service
public class DshIdentityService {
    private final SystemUserRepository users;
    public DshIdentityService(SystemUserRepository users) { this.users=users; }
    public DshLoginIdentity require(Authentication authentication) {
        if(authentication==null || !(authentication.getDetails() instanceof DshLoginIdentity identity) || identity.userId()==null)
            throw DshProblems.error(401,"DSH_RELOGIN_REQUIRED","请重新登录以取得包含用户标识的凭据。");
        if(identity.expiresAt()==null || !identity.expiresAt().isAfter(Instant.now()) || !enabled(identity.userId()))
            throw DshProblems.error(401,"DSH_USER_UNAVAILABLE","登录已过期或用户已停用。");
        return identity;
    }
    public boolean enabled(UUID id) { return users.findById(id).map(u->u.isEnabled()).orElse(false); }
}
