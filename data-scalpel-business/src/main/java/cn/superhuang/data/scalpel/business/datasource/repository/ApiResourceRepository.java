package cn.superhuang.data.scalpel.business.datasource.repository;

import cn.superhuang.data.scalpel.business.datasource.domain.ApiResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiResourceRepository extends JpaRepository<ApiResource, UUID> {

    List<ApiResource> findAllByDataSourceIdOrderByNameAsc(UUID dataSourceId);

    Optional<ApiResource> findByIdAndDataSourceId(UUID id, UUID dataSourceId);

    boolean existsByDataSourceIdAndCode(UUID dataSourceId, String code);

    boolean existsByDataSourceId(UUID dataSourceId);
}
