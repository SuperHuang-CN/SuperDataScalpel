package cn.superhuang.data.scalpel.business.operations.service;
import cn.superhuang.data.scalpel.business.operations.domain.AlertRuleType;
import cn.superhuang.data.scalpel.business.operations.web.response.AlertRecipientResponse;
import cn.superhuang.data.scalpel.business.system.access.domain.*;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AlertRecipientService {
    private final SystemUserRepository users;
    private final OperationsAccess access;
    private final SearchEngine search;
    public AlertRecipientService(SystemUserRepository users, OperationsAccess access, SearchEngine search) { this.users = users; this.access = access; this.search = search; }
    @Transactional(readOnly = true)
    public PageResponse<AlertRecipientResponse> search(AlertRuleType type, String keyword, int page, int size) {
        var actor = access.actor(); actor.require("alert.manage"); actor.require(type.permission());
        var p = search.search(new SearchRequest(null, page, size, "displayName"), SystemUser.class, users, (r, q, b) -> {
            var sq = q.subquery(UUID.class); var rp = sq.from(SystemRolePermission.class); var permission = sq.from(SystemPermission.class);
            sq.select(rp.get("id")).where(b.equal(rp.get("roleId"), r.get("roleId")), b.equal(rp.get("permissionId"), permission.get("id")),
                    b.equal(permission.get("code"), type.permission()), b.isTrue(permission.get("active")));
            var available = b.and(b.isTrue(r.get("enabled")), b.exists(sq));
            if (keyword == null || keyword.isBlank()) return available;
            String pattern = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return b.and(available, b.or(b.like(r.get("displayName"), pattern, '\\'), b.like(r.get("username"), pattern, '\\')));
        });
        return new PageResponse<>(p.getContent().stream().map(u -> new AlertRecipientResponse(u.getId(), u.getUsername(), u.getDisplayName())).toList(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
}
