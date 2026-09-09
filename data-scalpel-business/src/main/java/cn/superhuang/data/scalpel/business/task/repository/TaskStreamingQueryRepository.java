package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStreamingQueryRepository extends JpaRepository<TaskStreamingQuery, UUID> {
    List<TaskStreamingQuery> findAllByDeploymentIdIn(java.util.Collection<UUID> deploymentIds);
    List<TaskStreamingQuery> findAllByDeploymentIdOrderByOutputNodeNameAsc(UUID deploymentId);
    Optional<TaskStreamingQuery> findByDeploymentIdAndOutputNodeId(UUID deploymentId, UUID outputNodeId);
    Optional<TaskStreamingQuery> findByDeploymentIdAndOutputNodeIdAndOutputWriteId(
            UUID deploymentId,
            UUID outputNodeId,
            UUID outputWriteId
    );
    void deleteAllByDeploymentId(UUID deploymentId);
}
