package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewaySubscriptionEntity;
import cn.superhuang.superapigateway.controlplane.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewaySubscriptionRepository extends JpaRepository<GatewaySubscriptionEntity, UUID> {
    Optional<GatewaySubscriptionEntity> findByConsumerIdAndServiceId(UUID consumerId, UUID serviceId);
    List<GatewaySubscriptionEntity> findAllByStatus(SubscriptionStatus status);
    Page<GatewaySubscriptionEntity> findAllByConsumerId(UUID consumerId, Pageable pageable);
    Page<GatewaySubscriptionEntity> findAllByServiceId(UUID serviceId, Pageable pageable);
    long countByServiceIdAndStatus(UUID serviceId, SubscriptionStatus status);
    void deleteAllByConsumerId(UUID consumerId);
    void deleteAllByServiceId(UUID serviceId);
    Optional<GatewaySubscriptionEntity> findBySourceAndExternalId(String source, String externalId);
}
