package cn.superhuang.data.scalpel.business.compute.repository;

import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ComputeEngineRepository extends SearchRepository<ComputeEngine, UUID> {

    Optional<ComputeEngine> findByNameIgnoreCase(String name);

    boolean existsByCommandTopicAndIdNot(String commandTopic, UUID id);

    boolean existsByRunnerEventTopicAndIdNot(String runnerEventTopic, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select engine from ComputeEngine engine where engine.id = :id")
    Optional<ComputeEngine> findByIdForUpdate(UUID id);
}
