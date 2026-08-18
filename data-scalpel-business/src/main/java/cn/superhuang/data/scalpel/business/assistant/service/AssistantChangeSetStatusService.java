package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantChangeSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssistantChangeSetStatusService {

    private final AssistantChangeSetRepository repository;

    public AssistantChangeSetStatusService(AssistantChangeSetRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markStale(UUID id, String reason) {
        repository.findById(id).ifPresent(changeSet -> {
            if (changeSet.getStatus() == AssistantChangeSetStatus.PENDING) {
                changeSet.markStale(reason);
                repository.saveAndFlush(changeSet);
            }
        });
    }

    @Transactional
    public void markFailed(UUID id, String username, String reason) {
        repository.findById(id).ifPresent(changeSet -> {
            if (changeSet.getStatus() == AssistantChangeSetStatus.PENDING) {
                changeSet.fail(username, reason, Instant.now());
                repository.saveAndFlush(changeSet);
            }
        });
    }
}
