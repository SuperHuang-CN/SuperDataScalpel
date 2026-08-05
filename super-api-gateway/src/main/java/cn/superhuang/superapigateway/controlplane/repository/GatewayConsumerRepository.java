package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewayConsumerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface GatewayConsumerRepository extends JpaRepository<GatewayConsumerEntity, UUID> {
    boolean existsByCode(String code);
    Optional<GatewayConsumerEntity> findBySourceAndExternalId(String source, String externalId);
}
