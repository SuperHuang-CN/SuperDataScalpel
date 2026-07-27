package cn.superhuang.data.scalpel.business.service.consumer.subscription.repository;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscription;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiServiceSubscriptionRepository extends SearchRepository<ApiServiceSubscription, UUID> {

    boolean existsByConsumerIdAndDataServiceId(UUID consumerId, UUID dataServiceId);

    boolean existsByConsumerId(UUID consumerId);

    boolean existsByDataServiceId(UUID dataServiceId);

    List<ApiServiceSubscription> findAllByConsumerId(UUID consumerId);

    List<ApiServiceSubscription> findAllByDataServiceId(UUID dataServiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from ApiServiceSubscription subscription where subscription.id = :id")
    Optional<ApiServiceSubscription> findByIdForUpdate(@Param("id") UUID id);
}
