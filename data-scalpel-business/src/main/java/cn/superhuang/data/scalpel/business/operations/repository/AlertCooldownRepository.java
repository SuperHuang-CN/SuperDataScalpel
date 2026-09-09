package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertCooldownRepository extends SearchRepository<AlertCooldown, UUID> {
    Optional<AlertCooldown> findByScopeKey(String scopeKey);
}
