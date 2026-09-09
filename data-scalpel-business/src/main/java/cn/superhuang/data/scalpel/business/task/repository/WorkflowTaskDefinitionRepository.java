package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.WorkflowTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTaskDefinitionRepository extends JpaRepository<WorkflowTaskDefinition, UUID> {
    Optional<WorkflowTaskDefinition> findByTaskId(UUID taskId);
}
