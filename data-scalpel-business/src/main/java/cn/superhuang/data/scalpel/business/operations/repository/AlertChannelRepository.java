package cn.superhuang.data.scalpel.business.operations.repository;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;

public interface AlertChannelRepository extends SearchRepository<AlertChannel, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AlertChannel c where c.id = :id")
    Optional<AlertChannel> lockById(UUID id);
}
