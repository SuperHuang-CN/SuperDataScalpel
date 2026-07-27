package cn.superhuang.data.scalpel.engine.deployment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface EngineDeploymentRepository extends JpaRepository<EngineDeployment, UUID> {

    Optional<EngineDeployment> findByEngineCodeAndServiceId(String engineCode, UUID serviceId);

    List<EngineDeployment> findAllByEngineCodeAndStatus(String engineCode, EngineDeploymentRecordStatus status);

    List<EngineDeployment> findAllByEngineCodeAndStatusIn(
            String engineCode, Collection<EngineDeploymentRecordStatus> statuses
    );

    boolean existsByEngineCodeAndDataSourceIdAndStatus(
            String engineCode, UUID dataSourceId, EngineDeploymentRecordStatus status
    );

    boolean existsByEngineCodeAndDataSourceIdAndStatusIn(
            String engineCode, UUID dataSourceId, Collection<EngineDeploymentRecordStatus> statuses
    );
}
