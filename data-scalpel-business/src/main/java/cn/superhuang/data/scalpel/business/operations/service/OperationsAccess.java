package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.AlertRuleType;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.service.SystemAccessService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class OperationsAccess {
    private final SystemAccessService access;
    private final SystemUserRepository users;
    public OperationsAccess(SystemAccessService access, SystemUserRepository users) {
        this.access = access; this.users = users;
    }
    public Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw denied();
        var user = users.findByUsername(authentication.getName()).filter(u -> u.isEnabled()).orElseThrow(OperationsAccess::denied);
        var permissions = access.findAuthenticationUser(user.getUsername()).orElseThrow(OperationsAccess::denied).permissionCodes();
        return new Actor(user.getId(), user.getUsername(), Set.copyOf(permissions));
    }
    public boolean recipientCanView(UUID userId, AlertRuleType type) {
        return canUser(userId, type.permission());
    }
    public boolean canUser(UUID userId, String permission) {
        return users.findById(userId).filter(u -> u.isEnabled())
                .flatMap(u -> access.findAuthenticationUser(u.getUsername()))
                .map(u -> u.permissionCodes().contains(permission)).orElse(false);
    }
    public record Actor(UUID id, String name, Set<String> permissions) {
        public boolean has(String permission) { return permissions.contains(permission); }
        public void require(String permission) { if (!has(permission)) throw denied(); }
        public List<AlertRuleType> visibleTypes() {
            return Arrays.stream(AlertRuleType.values()).filter(t -> has(t.permission())).toList();
        }
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "没有当前操作或来源资源的访问权限"); }
}
