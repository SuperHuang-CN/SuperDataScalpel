package cn.superhuang.data.scalpel.business.quality.service;

import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.ResultDetailStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.service.QualityFailureSampleService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityResultCompatibilityTest {
    private final UUID modelId = UUID.randomUUID();
    private final UUID publicRunId = UUID.randomUUID();
    private final UUID executionRunId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final byte[] sample = "retained-sample".getBytes(StandardCharsets.UTF_8);
    private final TaskRunArtifactStorage storage = mock(TaskRunArtifactStorage.class);
    private final TaskRunRepository runs = mock(TaskRunRepository.class);
    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final ModelQualityOverviewService overview;
    private final QualityFailureSampleService samples;

    @SuppressWarnings("unchecked")
    QualityResultCompatibilityTest() {
        var models = mock(DataModelRepository.class);
        var tasks = mock(DataTaskRepository.class);
        ObjectProvider<TaskRunArtifactStorage> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(models.existsById(modelId)).thenReturn(true);
        when(tasks.findAllById(any())).thenReturn(List.of());
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn(publicRunId);
        when(run.getTaskId()).thenReturn(UUID.randomUUID());
        when(run.getExecutionRunId()).thenReturn(executionRunId);
        when(run.getExternalExecutionId()).thenReturn(executionId);
        when(run.getTaskType()).thenReturn(TaskType.SPARK_MODEL_QUALITY);
        when(run.getStatus()).thenReturn(TaskRunStatus.SUCCESS);
        when(run.getAttempt()).thenReturn(1);
        when(run.getResultObjectKey()).thenReturn("result.json");
        when(run.getQualityConclusion()).thenReturn(QualityConclusion.FAILED);
        when(run.getQualityTotalRules()).thenReturn(1L);
        when(run.getQualityPassedRules()).thenReturn(0L);
        when(run.getQualityFailedRules()).thenReturn(1L);
        when(run.getQualitySkippedRules()).thenReturn(0L);
        when(run.getQualityCheckedRows()).thenReturn(100000L);
        when(run.getQualityRuleSnapshotAt()).thenReturn(Instant.EPOCH);
        when(run.getEndedAt()).thenReturn(Instant.EPOCH.plusSeconds(10));
        when(runs.findById(publicRunId)).thenReturn(Optional.of(run));
        when(runs.findFirstByQualityTargetModelIdOrderByQueuedAtDesc(modelId)).thenReturn(Optional.of(run));
        when(runs.findFirstByQualityTargetModelIdAndStatusAndQualityConclusionIsNotNullAndQualityRuleSnapshotAtIsNotNullOrderByEndedAtDesc(
                modelId, TaskRunStatus.SUCCESS)).thenReturn(Optional.of(run));
        overview = new ModelQualityOverviewService(models, runs, tasks, provider, mapper,
                mock(PlatformTransactionManager.class));
        samples = new QualityFailureSampleService(runs, provider, null, mapper);
        when(storage.readIfPresent(eq("task-runs/%s/attempts/1/quality/samples/%s.parquet"
                .formatted(executionRunId, ruleId)), anyInt())).thenReturn(Optional.of(sample));
    }

    @Test
    void readsUnchangedQualityDetailsAndDownloadsSamplesAcrossSupportedResultVersions() throws Exception {
        for (int version = 5; version <= 11; version++) {
            store(version, executionRunId);
            var result = overview.get(modelId);
            assertEquals(ResultDetailStatus.AVAILABLE, result.resultDetailStatus(), "version " + version);
            assertEquals(3225L, result.ruleResults().getFirst().metric().violationCount());
            assertArrayEquals(sample, samples.download(publicRunId, ruleId).content());
        }
    }

    @Test
    void stillRejectsUnsupportedVersionsAndMismatchedExecutionIdentity() throws Exception {
        for (int version : new int[]{3, 12}) {
            store(version, executionRunId);
            assertEquals(ResultDetailStatus.INVALID, overview.get(modelId).resultDetailStatus());
            assertThrows(ResponseStatusException.class, () -> samples.download(publicRunId, ruleId));
        }
        store(11, publicRunId);
        assertEquals(ResultDetailStatus.INVALID, overview.get(modelId).resultDetailStatus());
    }

    private void store(int version, UUID artifactRunId) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sample));
        String json = """
                {"schemaVersion":%d,"executionId":"%s","runId":"%s","attempt":1,
                 "taskType":"SPARK_MODEL_QUALITY","state":"SUCCESS","error":null,
                 "affectedRows":null,"nodeResults":[],"qualityResult":{
                  "conclusion":"FAILED","totalRules":1,"passedRules":0,"failedRules":1,
                  "skippedRules":0,"checkedRows":100000,"technicalFailure":null,
                  "skippedRuleResults":[],"ruleResults":[{
                   "ruleId":"%s","ruleName":"category required","ruleType":"NOT_NULL",
                   "severity":"MAJOR","state":"FAILED","durationMs":10,
                   "metric":{"kind":"VIOLATION","violationCount":3225,"violationPercent":3.225,
                    "toleranceMetric":"COUNT","toleranceValue":0},
                   "sample":{"status":"AVAILABLE","sampledRows":100,"violationRows":3225,
                    "truncated":true,"sizeBytes":%d,"sha256":"%s","rowLocatable":false,
                    "columns":[{"fieldId":null,"code":"__ds_reason","name":"Reason",
                     "type":{"type":"STRING"},"primaryKey":false,"diagnostic":true}]}}]}}
                """.formatted(version, executionId, artifactRunId, ruleId, sample.length, digest);
        when(storage.readIfPresent(eq("result.json"), anyInt()))
                .thenReturn(Optional.of(json.getBytes(StandardCharsets.UTF_8)));
    }
}
