package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SqlDataServiceParameterRepository extends JpaRepository<SqlDataServiceParameter, UUID> {

    List<SqlDataServiceParameter> findAllByDataServiceIdOrderBySortOrderAsc(UUID dataServiceId);

    List<SqlDataServiceParameter> findAllByDataServiceIdInOrderByDataServiceIdAscSortOrderAsc(Collection<UUID> dataServiceIds);

    void deleteAllByDataServiceId(UUID dataServiceId);
}
