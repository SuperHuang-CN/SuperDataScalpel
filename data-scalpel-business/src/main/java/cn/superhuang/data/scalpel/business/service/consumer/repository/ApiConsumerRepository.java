package cn.superhuang.data.scalpel.business.service.consumer.repository;

import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApiConsumerRepository extends SearchRepository<ApiConsumer, UUID> {

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select consumer from ApiConsumer consumer where consumer.id = :id")
    Optional<ApiConsumer> findByIdForUpdate(@Param("id") UUID id);
}
