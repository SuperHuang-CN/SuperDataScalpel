package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageAggregate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SparkJarLineageAggregateRepository extends JpaRepository<SparkJarLineageAggregate, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select aggregate from SparkJarLineageAggregate aggregate
            where aggregate.taskId = :taskId and aggregate.definitionVersion = :definitionVersion
              and aggregate.jarSha256 = :jarSha256
            """)
    Optional<SparkJarLineageAggregate> findForUpdate(UUID taskId, int definitionVersion, String jarSha256);
}
