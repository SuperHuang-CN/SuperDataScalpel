package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpatialDataServiceDefinitionRepository extends JpaRepository<SpatialDataServiceDefinition, UUID> {
    Optional<SpatialDataServiceDefinition> findByDataServiceId(UUID dataServiceId);
    List<SpatialDataServiceDefinition> findAllByModelIdIn(List<UUID> modelIds);
    List<SpatialDataServiceDefinition> findAllByModelId(UUID modelId);
    List<SpatialDataServiceDefinition> findAllByDataServiceIdIn(List<UUID> dataServiceIds);
    void deleteByDataServiceId(UUID dataServiceId);
}
