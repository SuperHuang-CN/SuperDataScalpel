package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRun;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AssistantRunRepository extends JpaRepository<AssistantRun, UUID> {

    boolean existsByModelId(UUID modelId);

    boolean existsBySessionIdAndStatus(UUID sessionId, AssistantRunStatus status);

    List<AssistantRun> findAllBySessionIdAndStatusAndStartedAtBefore(
            UUID sessionId,
            AssistantRunStatus status,
            Instant startedBefore
    );

    Optional<AssistantRun> findFirstBySessionIdOrderByStartedAtDesc(UUID sessionId);
}
