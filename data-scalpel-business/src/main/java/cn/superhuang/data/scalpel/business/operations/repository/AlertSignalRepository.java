package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertSignalRepository extends SearchRepository<AlertSignal, UUID> {
    @Query("select s.id from AlertSignal s where s.status = 'PENDING' and s.nextAttemptAt <= :now order by s.nextAttemptAt, s.id")
    List<UUID> due(Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AlertSignal s where s.id = :id")
    Optional<AlertSignal> lockById(UUID id);
    long countByStatus(AlertSignalStatus status);
}
