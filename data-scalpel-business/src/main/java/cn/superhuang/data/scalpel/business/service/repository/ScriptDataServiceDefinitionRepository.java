package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.ScriptDataServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScriptDataServiceDefinitionRepository extends JpaRepository<ScriptDataServiceDefinition, UUID> {

    Optional<ScriptDataServiceDefinition> findByDataServiceId(UUID dataServiceId);

    List<ScriptDataServiceDefinition> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);

    List<ScriptDataServiceDefinition> findAllByDataSourceId(UUID dataSourceId);

    boolean existsByDataSourceId(UUID dataSourceId);

    void deleteByDataServiceId(UUID dataServiceId);
}
