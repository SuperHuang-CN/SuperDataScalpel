package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertDeliveryRepository extends SearchRepository<AlertDelivery, UUID> {
    @Query("select d.id from AlertDelivery d where (d.status = 'PENDING' and d.nextAttemptAt <= :now) or (d.status = 'SENDING' and d.leaseUntil <= :now) order by d.nextAttemptAt, d.id")
    List<UUID> due(Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AlertDelivery d where d.id = :id")
    Optional<AlertDelivery> lockById(UUID id);
    List<AlertDelivery> findAllByIncidentIdAndChannelIdOrderBySequenceAsc(UUID incidentId, UUID channelId);
    boolean existsByIncidentIdAndChannelIdAndEventType(UUID incidentId, UUID channelId, AlertEventType eventType);
}
