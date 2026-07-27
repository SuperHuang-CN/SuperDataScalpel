package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanvasTaskDefinitionRepository extends JpaRepository<CanvasTaskDefinition, UUID> {

    Optional<CanvasTaskDefinition> findByTaskId(UUID taskId);

    List<CanvasTaskDefinition> findAllByTaskIdIn(Collection<UUID> taskIds);
}

