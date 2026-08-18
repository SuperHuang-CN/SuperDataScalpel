package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceEngineDataSourceRegistrationRepository
        extends SearchRepository<ServiceEngineDataSourceRegistration, UUID> {

    Optional<ServiceEngineDataSourceRegistration> findByEngineIdAndDataSourceId(UUID engineId, UUID dataSourceId);

    boolean existsByEngineId(UUID engineId);

    boolean existsByDataSourceId(UUID dataSourceId);

    List<ServiceEngineDataSourceRegistration> findAllByEngineId(UUID engineId);

    List<ServiceEngineDataSourceRegistration> findAllByDataSourceId(UUID dataSourceId);
}
