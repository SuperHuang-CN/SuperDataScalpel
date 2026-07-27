package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataServiceDeploymentRepository extends JpaRepository<DataServiceDeployment, UUID> {

    Optional<DataServiceDeployment> findByDataServiceId(UUID dataServiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deployment from DataServiceDeployment deployment where deployment.dataServiceId = :dataServiceId")
    Optional<DataServiceDeployment> findByDataServiceIdForUpdate(@Param("dataServiceId") UUID dataServiceId);

    List<DataServiceDeployment> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);
}
