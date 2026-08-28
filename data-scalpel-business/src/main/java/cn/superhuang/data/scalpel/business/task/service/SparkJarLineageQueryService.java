package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarLineageIngestion;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarLineageIngestionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLineageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class SparkJarLineageQueryService {
    private final TaskRunRepository runRepository;
    private final SparkJarLineageIngestionRepository ingestionRepository;
    private final ObjectMapper objectMapper;

    public SparkJarLineageQueryService(
            TaskRunRepository runRepository,
            SparkJarLineageIngestionRepository ingestionRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.ingestionRepository = ingestionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TaskRunLineageResponse get(UUID runId) {
        TaskRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
        if (run.getTaskType() != TaskType.SPARK_JAR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "运行血缘状态只适用于批处理 Spark JAR");
        }
        return ingestionRepository.findByRunId(runId)
                .map(this::response)
                .orElseGet(() -> new TaskRunLineageResponse(
                        runId, terminal(run) ? "NOT_AVAILABLE" : "PENDING",
                        null, false, null, null, List.of(), null,
                        terminal(run) ? "当前运行没有可摄取的 v8 血缘结果" : null, null));
    }

    private TaskRunLineageResponse response(SparkJarLineageIngestion value) {
        List<TaskRunLineageResponse.Warning> warnings = value.getWarningsJson() == null
                ? List.of()
                : objectMapper.readValue(value.getWarningsJson(),
                new TypeReference<List<TaskRunLineageResponse.Warning>>() { });
        return new TaskRunLineageResponse(
                value.getRunId(), value.getStatus().name(), value.getCoverage(),
                value.isPublishedSnapshot(), value.getFlowCount(), value.getWarningCount(), warnings,
                value.getErrorCode(), value.getErrorDetail(), value.getCompletedAt());
    }

    private static boolean terminal(TaskRun run) {
        return switch (run.getStatus()) {
            case SUCCESS, FAILED, TIMED_OUT, CANCELLED, SKIPPED, STOPPED -> true;
            default -> false;
        };
    }
}
