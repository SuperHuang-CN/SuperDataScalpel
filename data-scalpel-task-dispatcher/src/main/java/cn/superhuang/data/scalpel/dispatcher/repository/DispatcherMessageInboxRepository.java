package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherMessageInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DispatcherMessageInboxRepository extends JpaRepository<DispatcherMessageInbox, UUID> {
    boolean existsByMessageId(UUID messageId);
    Optional<DispatcherMessageInbox> findByMessageId(UUID messageId);
}
