package cn.superhuang.data.scalpel.business.datasource.repository;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialFeatureResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpatialFeatureResourceRepository extends JpaRepository<SpatialFeatureResource, UUID> {
    List<SpatialFeatureResource> findAllByDataSourceIdOrderByNameAsc(UUID dataSourceId);
    Optional<SpatialFeatureResource> findByIdAndDataSourceId(UUID id, UUID dataSourceId);
    boolean existsByDataSourceIdAndCode(UUID dataSourceId, String code);
    boolean existsByDataSourceId(UUID dataSourceId);
}
