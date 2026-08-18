package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.ModelQualityTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelQualityTaskDefinitionRepository extends JpaRepository<ModelQualityTaskDefinition, UUID> {
    Optional<ModelQualityTaskDefinition> findByTaskId(UUID taskId);
    List<ModelQualityTaskDefinition> findAllByTaskIdIn(Collection<UUID> taskIds);
    List<ModelQualityTaskDefinition> findAllByModelId(UUID modelId);
    boolean existsByModelId(UUID modelId);
}
