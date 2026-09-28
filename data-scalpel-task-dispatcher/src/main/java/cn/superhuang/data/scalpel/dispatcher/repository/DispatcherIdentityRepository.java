package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatcherIdentityRepository extends JpaRepository<DispatcherIdentity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from DispatcherIdentity i where i.id = 'singleton'")
    java.util.Optional<DispatcherIdentity> lockIdentity();
}
