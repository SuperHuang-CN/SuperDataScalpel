package cn.superhuang.data.scalpel.business.service.repository;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineAccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceEngineAccessPolicyRepository extends JpaRepository<ServiceEngineAccessPolicy, UUID> {

    Optional<ServiceEngineAccessPolicy> findByEngineId(UUID engineId);

    void deleteByEngineId(UUID engineId);
}
