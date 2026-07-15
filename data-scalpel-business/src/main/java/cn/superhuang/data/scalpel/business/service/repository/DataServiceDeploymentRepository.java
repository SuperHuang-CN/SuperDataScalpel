package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DataServiceDeploymentRepository extends JpaRepository<DataServiceDeployment, UUID> {

    Optional<DataServiceDeployment> findByDataServiceId(UUID dataServiceId);

    List<DataServiceDeployment> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);
}
