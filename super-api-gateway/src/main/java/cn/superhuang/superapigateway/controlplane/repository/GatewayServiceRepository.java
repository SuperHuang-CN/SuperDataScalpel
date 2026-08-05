package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewayServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GatewayServiceRepository extends JpaRepository<GatewayServiceEntity, UUID> {
    boolean existsByCode(String code);
    Optional<GatewayServiceEntity> findBySourceAndExternalId(String source, String externalId);
}
