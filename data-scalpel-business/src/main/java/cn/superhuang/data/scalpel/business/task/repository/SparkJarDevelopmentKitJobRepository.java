package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitJob;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SparkJarDevelopmentKitJobRepository extends JpaRepository<SparkJarDevelopmentKitJob, UUID> {
    boolean existsByTaskIdAndStatusIn(UUID taskId, Collection<SparkJarDevelopmentKitStatus> statuses);
    Optional<SparkJarDevelopmentKitJob> findFirstByTaskIdOrderByCreatedAtDesc(UUID taskId);
    Optional<SparkJarDevelopmentKitJob> findFirstByTaskIdAndStatusOrderByCreatedAtDesc(
            UUID taskId, SparkJarDevelopmentKitStatus status);
    List<SparkJarDevelopmentKitJob> findAllByTaskId(UUID taskId);
    Optional<SparkJarDevelopmentKitJob> findByIdAndTaskId(UUID id, UUID taskId);
    void deleteAllByTaskId(UUID taskId);
    List<SparkJarDevelopmentKitJob> findTop100ByStatusAndArtifactExpiresAtLessThanEqualOrderByArtifactExpiresAtAsc(
            SparkJarDevelopmentKitStatus status, Instant now);
}
