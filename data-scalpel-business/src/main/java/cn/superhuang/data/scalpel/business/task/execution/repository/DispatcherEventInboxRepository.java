package cn.superhuang.data.scalpel.business.task.execution.repository;

import cn.superhuang.data.scalpel.business.task.execution.domain.DispatcherEventInboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DispatcherEventInboxRepository extends JpaRepository<DispatcherEventInboxMessage, UUID> {
    boolean existsByMessageId(UUID messageId);
    Optional<DispatcherEventInboxMessage> findByMessageId(UUID messageId);
}
