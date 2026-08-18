package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolInvocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface AssistantToolInvocationRepository extends JpaRepository<AssistantToolInvocation, UUID> {

    List<AssistantToolInvocation> findAllByRunIdOrderByCreatedAtAsc(UUID runId);
}
