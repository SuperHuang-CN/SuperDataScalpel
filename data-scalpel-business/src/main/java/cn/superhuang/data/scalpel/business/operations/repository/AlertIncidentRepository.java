package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertIncidentRepository extends SearchRepository<AlertIncident, UUID> {
    Optional<AlertIncident> findByEventKey(String eventKey);
    Optional<AlertIncident> findByActiveKey(String activeKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AlertIncident i where i.id = :id")
    Optional<AlertIncident> lockById(UUID id);
    @Query("select i.id from AlertIncident i where i.activeKey is not null order by i.lastEvaluatedAt, i.id")
    List<UUID> activeBatch(Pageable pageable);
}
