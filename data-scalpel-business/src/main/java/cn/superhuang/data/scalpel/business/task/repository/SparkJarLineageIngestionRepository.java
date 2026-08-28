package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SparkJarLineageIngestionRepository extends JpaRepository<SparkJarLineageIngestion, UUID> {
    Optional<SparkJarLineageIngestion> findByRunId(UUID runId);
    boolean existsByRunId(UUID runId);
}
