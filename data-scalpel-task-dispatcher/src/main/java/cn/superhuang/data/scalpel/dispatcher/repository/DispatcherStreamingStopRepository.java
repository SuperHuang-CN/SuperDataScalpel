package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherStreamingStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DispatcherStreamingStopRepository extends JpaRepository<DispatcherStreamingStop, UUID> {
    Optional<DispatcherStreamingStop> findByExecutionIdAndAttempt(UUID executionId, int attempt);
}
