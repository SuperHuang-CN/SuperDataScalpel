package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskExecutionOutboxService {

    private final TaskExecutionOutboxRepository repository;
    private final ExecutionMessageJsonCodec codec;

    public TaskExecutionOutboxService(TaskExecutionOutboxRepository repository, ExecutionMessageJsonCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public boolean hasForceTerminate(java.util.UUID runId, java.util.UUID executionId) {
        return repository.existsByAggregateIdAndExecutionIdAndMessageType(runId, executionId, "FORCE_TERMINATE_EXECUTION");
    }

    @Transactional
    public TaskExecutionOutboxMessage enqueue(String topic, ExecutionCommand command) {
        repository.findByMessageId(command.messageId()).ifPresent(existing -> {
            throw new IllegalStateException("执行消息 ID 已存在：" + command.messageId());
        });
        return repository.save(TaskExecutionOutboxMessage.pending(topic, command, codec.write(command)));
    }
}
