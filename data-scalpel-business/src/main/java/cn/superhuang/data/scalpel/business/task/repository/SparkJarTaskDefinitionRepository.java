package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SparkJarTaskDefinitionRepository extends JpaRepository<SparkJarTaskDefinition, UUID> {
    Optional<SparkJarTaskDefinition> findByTaskId(UUID taskId);
    List<SparkJarTaskDefinition> findAllByTaskIdIn(Collection<UUID> taskIds);
    boolean existsByJarObjectKey(String jarObjectKey);
}
