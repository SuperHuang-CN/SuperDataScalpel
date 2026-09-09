package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentExecutionMode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStreamingDeploymentRepository extends cn.superhuang.data.scalpel.search.SearchRepository<TaskStreamingDeployment, UUID> {
    Optional<TaskStreamingDeployment> findFirstByTaskIdAndDefinitionVersionOrderByCheckpointGenerationDesc(
            UUID taskId, int definitionVersion);
    Optional<TaskStreamingDeployment> findFirstByTaskIdAndDefinitionVersionAndExecutionModeOrderByCheckpointGenerationDesc(
            UUID taskId, int definitionVersion, StreamingDeploymentExecutionMode executionMode);
    Optional<TaskStreamingDeployment> findFirstByTaskIdOrderByDefinitionVersionDescCheckpointGenerationDesc(UUID taskId);
    Optional<TaskStreamingDeployment> findFirstByTaskIdAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
            UUID taskId, StreamingDeploymentExecutionMode executionMode);
    Optional<TaskStreamingDeployment> findFirstByTaskIdAndDefinitionVersionLessThanOrderByDefinitionVersionDescCheckpointGenerationDesc(
            UUID taskId, int definitionVersion);
    Optional<TaskStreamingDeployment> findFirstByTaskIdAndDefinitionVersionLessThanAndExecutionModeOrderByDefinitionVersionDescCheckpointGenerationDesc(
            UUID taskId, int definitionVersion, StreamingDeploymentExecutionMode executionMode);
    boolean existsByTaskIdAndActualStateIn(UUID taskId, Collection<StreamingDeploymentActualState> states);
    boolean existsByTaskIdAndExecutionModeAndActualStateIn(
            UUID taskId,
            StreamingDeploymentExecutionMode executionMode,
            Collection<StreamingDeploymentActualState> states
    );
    List<TaskStreamingDeployment> findAllByActualStateIn(Collection<StreamingDeploymentActualState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deployment from TaskStreamingDeployment deployment where deployment.id = :id")
    Optional<TaskStreamingDeployment> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deployment from TaskStreamingDeployment deployment
            where deployment.taskId = :taskId and deployment.definitionVersion = :definitionVersion
              and deployment.checkpointGeneration = :checkpointGeneration
            """)
    Optional<TaskStreamingDeployment> findByTaskIdAndDefinitionVersionAndCheckpointGenerationForUpdate(
            UUID taskId, int definitionVersion, int checkpointGeneration);
}
