package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SqlDataServiceDefinitionRepository extends JpaRepository<SqlDataServiceDefinition, UUID> {

    Optional<SqlDataServiceDefinition> findByDataServiceId(UUID dataServiceId);

    List<SqlDataServiceDefinition> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);

    boolean existsByDataSourceId(UUID dataSourceId);

    List<SqlDataServiceDefinition> findAllByDataSourceId(UUID dataSourceId);

    void deleteByDataServiceId(UUID dataServiceId);
}
