package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalSqlTaskDefinitionRepository extends JpaRepository<LocalSqlTaskDefinition, UUID> {

    Optional<LocalSqlTaskDefinition> findByTaskId(UUID taskId);

    List<LocalSqlTaskDefinition> findAllByTaskIdIn(Collection<UUID> taskIds);

    List<LocalSqlTaskDefinition> findAllByOutputModelId(UUID outputModelId);

    boolean existsByOutputModelId(UUID outputModelId);
}
