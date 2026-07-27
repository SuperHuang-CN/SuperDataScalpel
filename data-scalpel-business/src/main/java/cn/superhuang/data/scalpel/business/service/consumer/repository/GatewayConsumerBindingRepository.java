package cn.superhuang.data.scalpel.business.service.consumer.repository;

import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
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

public interface GatewayConsumerBindingRepository extends JpaRepository<GatewayConsumerBinding, UUID> {

    Optional<GatewayConsumerBinding> findByConsumerIdAndProvider(UUID consumerId, GatewayProvider provider);

    List<GatewayConsumerBinding> findAllByConsumerId(UUID consumerId);

    List<GatewayConsumerBinding> findAllByConsumerIdIn(Collection<UUID> consumerIds);

    boolean existsByConsumerId(UUID consumerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding
            from GatewayConsumerBinding binding
            where binding.consumerId = :consumerId and binding.provider = :provider
            """)
    Optional<GatewayConsumerBinding> findByConsumerIdAndProviderForUpdate(
            @Param("consumerId") UUID consumerId,
            @Param("provider") GatewayProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayConsumerBinding binding where binding.id = :id")
    Optional<GatewayConsumerBinding> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayConsumerBinding binding where binding.consumerId = :consumerId")
    List<GatewayConsumerBinding> findAllByConsumerIdForUpdate(@Param("consumerId") UUID consumerId);
}
