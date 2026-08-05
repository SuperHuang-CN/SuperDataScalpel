package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.ApiKeyStatus;
import cn.superhuang.superapigateway.controlplane.domain.GatewayApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayApiKeyRepository extends JpaRepository<GatewayApiKeyEntity, UUID> {
    boolean existsByConsumerIdAndName(UUID consumerId, String name);
    List<GatewayApiKeyEntity> findAllByConsumerIdOrderByCreatedAtDesc(UUID consumerId);
    List<GatewayApiKeyEntity> findAllByStatus(ApiKeyStatus status);
    void deleteAllByConsumerId(UUID consumerId);
    Optional<GatewayApiKeyEntity> findByConsumerIdAndSourceAndExternalId(
            UUID consumerId,
            String source,
            String externalId
    );
}
