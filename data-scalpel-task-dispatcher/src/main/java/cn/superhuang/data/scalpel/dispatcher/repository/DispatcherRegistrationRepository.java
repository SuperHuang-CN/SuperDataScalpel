package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatcherRegistrationRepository extends JpaRepository<DispatcherRegistration, UUID> {
    Optional<DispatcherRegistration> findFirstByOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from DispatcherRegistration registration order by registration.createdAt")
    List<DispatcherRegistration> findAllForUpdate();
}
