package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertRuleRepository extends SearchRepository<AlertRule, UUID> {
    Optional<AlertRule> findByScopeKey(String scopeKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AlertRule r where r.scopeKey = :scopeKey")
    Optional<AlertRule> lockByScopeKey(String scopeKey);
}
