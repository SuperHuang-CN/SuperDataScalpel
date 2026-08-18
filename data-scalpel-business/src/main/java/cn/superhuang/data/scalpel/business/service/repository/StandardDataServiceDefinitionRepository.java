package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.StandardDataServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StandardDataServiceDefinitionRepository extends JpaRepository<StandardDataServiceDefinition, UUID> {

    Optional<StandardDataServiceDefinition> findByDataServiceId(UUID dataServiceId);

    List<StandardDataServiceDefinition> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);

    boolean existsByModelId(UUID modelId);

    List<StandardDataServiceDefinition> findAllByModelIdIn(Collection<UUID> modelIds);

    List<StandardDataServiceDefinition> findAllByModelId(UUID modelId);

    void deleteByDataServiceId(UUID dataServiceId);
}
