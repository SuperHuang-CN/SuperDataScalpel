package cn.superhuang.data.scalpel.business.compute.repository;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ComputeEngineRepository extends SearchRepository<ComputeEngine, UUID> {

    @Query("""
            select e.id from ComputeEngine e left join EngineObservation o on o.engineId = e.id
            where e.registrationState = 'ACTIVE' and (o.attemptedAt is null or o.attemptedAt <= :before)
            and (o.leaseUntil is null or o.leaseUntil <= :now)
            order by o.attemptedAt nulls first, e.id
            """)
    java.util.List<UUID> observationCandidates(java.time.Instant before, java.time.Instant now,
                                              org.springframework.data.domain.Pageable pageable);

    Optional<ComputeEngine> findByNameIgnoreCase(String name);

    boolean existsByCommandTopicAndIdNot(String commandTopic, UUID id);

    boolean existsByRunnerEventTopicAndIdNot(String runnerEventTopic, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select engine from ComputeEngine engine where engine.id = :id")
    Optional<ComputeEngine> findByIdForUpdate(UUID id);
}
