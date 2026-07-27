package cn.superhuang.data.scalpel.business.service.consumer.credential.repository;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.ApiConsumerCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiConsumerCredentialRepository extends JpaRepository<ApiConsumerCredential, UUID> {

    List<ApiConsumerCredential> findAllByConsumerIdOrderByCreatedAtDesc(UUID consumerId);

    boolean existsByConsumerId(UUID consumerId);

    long countByConsumerId(UUID consumerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from ApiConsumerCredential credential where credential.id = :id")
    Optional<ApiConsumerCredential> findByIdForUpdate(@Param("id") UUID id);
}
