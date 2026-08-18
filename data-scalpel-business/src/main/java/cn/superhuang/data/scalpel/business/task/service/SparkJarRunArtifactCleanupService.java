package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunJarCleanupStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SparkJarRunArtifactCleanupService {
    private static final Logger log = LoggerFactory.getLogger(SparkJarRunArtifactCleanupService.class);
    private static final List<TaskRunStatus> TERMINAL = List.of(
            TaskRunStatus.SUCCESS, TaskRunStatus.FAILED, TaskRunStatus.TIMED_OUT,
            TaskRunStatus.CANCELLED, TaskRunStatus.SKIPPED, TaskRunStatus.STOPPED);

    private final TaskRunRepository repository;
    private final ObjectProvider<TaskRunArtifactStorage> storageProvider;

    public SparkJarRunArtifactCleanupService(TaskRunRepository repository,
                                             ObjectProvider<TaskRunArtifactStorage> storageProvider) {
        this.repository = repository;
        this.storageProvider = storageProvider;
    }

    @Scheduled(fixedDelayString = "${datascalpel.task.spark-jar.cleanup-delay:PT1M}")
    public void cleanup() {
        TaskRunArtifactStorage storage = storageProvider.getIfAvailable();
        if (storage == null) return;
        repository.findAllByTaskTypeInAndUserJarCleanupStatusAndStatusIn(
                        List.of(TaskType.SPARK_JAR, TaskType.SPARK_STREAMING_JAR),
                        TaskRunJarCleanupStatus.PENDING, TERMINAL)
                .stream().limit(100).forEach(run -> {
                    try {
                        storage.delete(run.getRunUserJarObjectKey());
                        markCompleted(run.getId());
                    } catch (RuntimeException exception) {
                        log.warn("Spark JAR run artifact cleanup failed: runId={}", run.getId(), exception);
                    }
                });
    }

    void markCompleted(UUID runId) {
        repository.findById(runId).ifPresent(run -> {
            run.userJarCleanupCompleted();
            repository.save(run);
        });
    }
}
