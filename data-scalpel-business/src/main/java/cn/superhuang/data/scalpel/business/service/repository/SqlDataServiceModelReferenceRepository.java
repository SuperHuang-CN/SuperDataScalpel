package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceModelReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SqlDataServiceModelReferenceRepository extends JpaRepository<SqlDataServiceModelReference, UUID> {

    List<SqlDataServiceModelReference> findAllByDataServiceIdOrderBySortOrderAsc(UUID dataServiceId);

    List<SqlDataServiceModelReference> findAllByDataServiceIdInOrderByDataServiceIdAscSortOrderAsc(
            Collection<UUID> dataServiceIds
    );

    List<SqlDataServiceModelReference> findAllByModelIdIn(Collection<UUID> modelIds);

    List<SqlDataServiceModelReference> findAllByModelIdOrderByDataServiceIdAscSortOrderAsc(UUID modelId);

    boolean existsByModelId(UUID modelId);

    void deleteAllByDataServiceId(UUID dataServiceId);
}
