package cn.superhuang.superapigateway.controlplane.repository;

import cn.superhuang.superapigateway.controlplane.domain.GatewayConfigState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GatewayConfigStateRepository extends JpaRepository<GatewayConfigState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from GatewayConfigState state where state.id = :id")
    Optional<GatewayConfigState> findForUpdate(long id);
}
