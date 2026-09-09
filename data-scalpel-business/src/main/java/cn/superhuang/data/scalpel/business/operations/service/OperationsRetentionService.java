package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OperationsRetentionService {
    private final EntityManager em;
    private final OperationsProperties properties;
    public OperationsRetentionService(EntityManager em, OperationsProperties properties) { this.em = em; this.properties = properties; }
    @Scheduled(scheduler = "operationsScheduler", initialDelayString = "10m", fixedDelayString = "10m")
    @Transactional
    public void clean() {
        Instant now = Instant.now();
        remove("AlertSignal", "e.status='PROCESSED' and e.updatedAt<:cutoff", now.minus(properties.signalRetentionDays(), ChronoUnit.DAYS));
        remove("InAppNotification", "e.createdAt<:cutoff", now.minus(properties.notificationRetentionDays(), ChronoUnit.DAYS));
        remove("AlertDelivery", "e.status in ('SENT','FAILED','SUPPRESSED') and e.updatedAt<:cutoff", now.minus(properties.deliveryRetentionDays(), ChronoUnit.DAYS));
        var ids = em.createQuery("""
                select i.id from AlertIncident i where i.status='CLOSED' and i.closedAt<:cutoff
                and not exists (select n.id from InAppNotification n where n.incidentId=i.id)
                and not exists (select d.id from AlertDelivery d where d.incidentId=i.id)
                order by i.closedAt
                """, UUID.class).setParameter("cutoff", now.minus(properties.incidentRetentionDays(), ChronoUnit.DAYS)).setMaxResults(100).getResultList();
        for (UUID id : ids) {
            // Lock and recheck because another transaction may be completing a notification.
            var i = em.find(AlertIncident.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (i == null || i.getStatus() != AlertHandlingStatus.CLOSED) continue;
            long references = em.createQuery("select count(d) from AlertDelivery d where d.incidentId=:id", Long.class).setParameter("id", id).getSingleResult()
                    + em.createQuery("select count(n) from InAppNotification n where n.incidentId=:id", Long.class).setParameter("id", id).getSingleResult();
            if (references != 0) continue;
            em.createQuery("delete from AlertAction a where a.incidentId=:id").setParameter("id", id).executeUpdate();
            em.remove(i);
        }
        remove("AlertCooldown", "e.nextAllowedAt<:cutoff", now.minus(30, ChronoUnit.DAYS));
    }
    private void remove(String entity, String where, Instant cutoff) {
        var ids = em.createQuery("select e.id from " + entity + " e where " + where + " order by e.id", UUID.class)
                .setParameter("cutoff", cutoff).setMaxResults(100).getResultList();
        if (!ids.isEmpty()) em.createQuery("delete from " + entity + " e where e.id in :ids and " + where)
                .setParameter("ids", ids).setParameter("cutoff", cutoff).executeUpdate();
    }
}
