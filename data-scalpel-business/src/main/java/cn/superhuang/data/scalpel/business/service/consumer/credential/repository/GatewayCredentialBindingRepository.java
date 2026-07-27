package cn.superhuang.data.scalpel.business.service.consumer.credential.repository;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;
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

public interface GatewayCredentialBindingRepository extends JpaRepository<GatewayCredentialBinding, UUID> {

    List<GatewayCredentialBinding> findAllByCredentialId(UUID credentialId);

    List<GatewayCredentialBinding> findAllByCredentialIdIn(Collection<UUID> credentialIds);

    boolean existsByCredentialId(UUID credentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding from GatewayCredentialBinding binding
            where binding.credentialId = :credentialId and binding.provider = :provider
            """)
    Optional<GatewayCredentialBinding> findByCredentialIdAndProviderForUpdate(
            @Param("credentialId") UUID credentialId,
            @Param("provider") GatewayProvider provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayCredentialBinding binding where binding.id = :id")
    Optional<GatewayCredentialBinding> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select binding from GatewayCredentialBinding binding where binding.credentialId = :credentialId")
    List<GatewayCredentialBinding> findAllByCredentialIdForUpdate(@Param("credentialId") UUID credentialId);
}
