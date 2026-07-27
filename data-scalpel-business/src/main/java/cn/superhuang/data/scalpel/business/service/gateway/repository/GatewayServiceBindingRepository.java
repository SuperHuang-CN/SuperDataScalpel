package cn.superhuang.data.scalpel.business.service.gateway.repository;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayServiceBindingRepository extends JpaRepository<GatewayServiceBinding, UUID> {

    List<GatewayServiceBinding> findAllByDataServiceId(UUID dataServiceId);

    List<GatewayServiceBinding> findAllByDataServiceIdIn(Collection<UUID> dataServiceIds);

    boolean existsByDataServiceId(UUID dataServiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding
            from GatewayServiceBinding binding
            where binding.dataServiceId = :dataServiceId and binding.provider = :provider
            """)
    Optional<GatewayServiceBinding> findByDataServiceIdAndProviderForUpdate(
            @Param("dataServiceId") UUID dataServiceId,
            @Param("provider") GatewayProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayServiceBinding binding where binding.id = :id")
    Optional<GatewayServiceBinding> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayServiceBinding binding where binding.dataServiceId = :dataServiceId")
    List<GatewayServiceBinding> findAllByDataServiceIdForUpdate(@Param("dataServiceId") UUID dataServiceId);
}
