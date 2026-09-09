package cn.superhuang.data.scalpel.business.operations.service;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.InAppNotificationRepository;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;

@Service
public class InAppNotificationService {
    private final InAppNotificationRepository notifications;
    private final OperationsAccess access;
    private final SearchEngine search;
    public InAppNotificationService(InAppNotificationRepository notifications, OperationsAccess access, SearchEngine search) {
        this.notifications = notifications; this.access = access; this.search = search;
    }
    private Specification<InAppNotification> scope() {
        var a = access.actor();
        return (r, q, b) -> b.and(b.equal(r.get("userId"), a.id()), r.get("ruleType").in(a.visibleTypes()));
    }
    @Transactional(readOnly = true)
    public PageResponse<InAppNotificationResponse> search(SearchRequest request) {
        var p = search.search(request, InAppNotification.class, notifications, scope());
        return new PageResponse<>(p.map(InAppNotificationResponse::from).getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unread() {
        return new UnreadNotificationCountResponse(notifications.count(scope().and((r, q, b) -> b.isNull(r.get("readAt")))));
    }
    @Transactional
    public InAppNotificationResponse read(UUID id) {
        var a = access.actor(); var n = notifications.findById(id).filter(v -> v.getUserId().equals(a.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在"));
        a.require(n.getRuleType().permission()); if (n.getReadAt() == null) n.setReadAt(Instant.now());
        return InAppNotificationResponse.from(n);
    }
}
