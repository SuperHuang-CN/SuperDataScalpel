package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, UUID> {

    Page<AssistantMessage> findAllBySessionId(UUID sessionId, Pageable pageable);

    List<AssistantMessage> findTop20BySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
