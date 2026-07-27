package cn.superhuang.data.scalpel.business.service.consumer.subscription.repository;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewaySubscriptionBindingRepository extends JpaRepository<GatewaySubscriptionBinding, UUID> {

    List<GatewaySubscriptionBinding> findAllBySubscriptionId(UUID subscriptionId);

    List<GatewaySubscriptionBinding> findAllBySubscriptionIdIn(Collection<UUID> subscriptionIds);

    boolean existsBySubscriptionId(UUID subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding from GatewaySubscriptionBinding binding
            where binding.subscriptionId = :subscriptionId and binding.provider = :provider
            """)
    Optional<GatewaySubscriptionBinding> findBySubscriptionIdAndProviderForUpdate(
            @Param("subscriptionId") UUID subscriptionId,
            @Param("provider") GatewayProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewaySubscriptionBinding binding where binding.id = :id")
    Optional<GatewaySubscriptionBinding> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding from GatewaySubscriptionBinding binding
            where binding.subscriptionId = :subscriptionId
            """)
    List<GatewaySubscriptionBinding> findAllBySubscriptionIdForUpdate(
            @Param("subscriptionId") UUID subscriptionId
    );
}
