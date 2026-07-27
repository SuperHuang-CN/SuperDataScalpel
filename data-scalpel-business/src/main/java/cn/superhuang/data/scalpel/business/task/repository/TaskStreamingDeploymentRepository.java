package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStreamingDeploymentRepository extends JpaRepository<TaskStreamingDeployment, UUID> {
    Optional<TaskStreamingDeployment> findByTaskIdAndDefinitionVersion(UUID taskId, int definitionVersion);
    Optional<TaskStreamingDeployment> findFirstByTaskIdOrderByDefinitionVersionDesc(UUID taskId);
    boolean existsByTaskIdAndActualStateIn(UUID taskId, Collection<StreamingDeploymentActualState> states);
    List<TaskStreamingDeployment> findAllByActualStateIn(Collection<StreamingDeploymentActualState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deployment from TaskStreamingDeployment deployment where deployment.id = :id")
    Optional<TaskStreamingDeployment> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deployment from TaskStreamingDeployment deployment
            where deployment.taskId = :taskId and deployment.definitionVersion = :definitionVersion
            """)
    Optional<TaskStreamingDeployment> findByTaskIdAndDefinitionVersionForUpdate(UUID taskId, int definitionVersion);
}
