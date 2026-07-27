package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatcherIdentityRepository extends JpaRepository<DispatcherIdentity, String> {
}
