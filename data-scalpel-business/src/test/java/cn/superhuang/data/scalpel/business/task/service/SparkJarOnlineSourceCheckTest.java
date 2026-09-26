package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineSelectionService;
import cn.superhuang.data.scalpel.business.compute.service.SparkExecutionResourceConfigurationService;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.web.request.SaveSparkJarOnlineSourceRequest;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarOnlineCompilationResponse;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SparkJarOnlineSourceCheckTest {
    private final UUID taskId = UUID.randomUUID();
    private final DataTaskRepository tasks = mock(DataTaskRepository.class);
    private final SparkJarTaskDefinitionRepository definitions = mock(SparkJarTaskDefinitionRepository.class);
    private final TaskRunArtifactStorage storage = mock(TaskRunArtifactStorage.class);
    private final TaskCompilationService compiler = mock(TaskCompilationService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SparkJarTaskDefinitionService service = new SparkJarTaskDefinitionService(
            tasks, definitions, mock(SparkJarTaskResourceBindingRepository.class), mock(DataModelRepository.class),
            mock(DataSourceRepository.class), mock(DataSourceRuntimeService.class), mock(ComputeEngineSelectionService.class),
            mock(SparkExecutionResourceConfigurationService.class), storage, compiler,
            JsonMapper.builderWithJackson2Defaults().build(), transactions);

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"SPARK_JAR", "SPARK_STREAMING_JAR"})
    void checksWithoutSavingSourceCreatingDefinitionOrReplacingJar(TaskType type) throws Exception {
        when(tasks.findById(taskId)).thenReturn(Optional.of(DataTask.create("check", null, type, null)));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        String source = "package demo;\npublic class Job {}";
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        when(compiler.compileSparkJarSource(any())).thenAnswer(invocation -> {
            SparkJarSourceCompilationRequest request = invocation.getArgument(0);
            assertEquals(type == TaskType.SPARK_STREAMING_JAR ? "STREAMING" : "BATCH", request.jobMode().name());
            assertEquals(source, request.sourceCode());
            return new SparkJarSourceCompilationResponse(request.requestId(), true, 12, sha, "jar", new byte[]{1}, List.of());
        });
        var result = service.checkOnlineSource(taskId, new SaveSparkJarOnlineSourceRequest(source));
        assertEquals(SparkJarOnlineCompilationResponse.Status.SUCCEEDED, result.status());
        assertFalse(result.source().persisted());
        verify(definitions).findByTaskId(taskId);
        verifyNoMoreInteractions(definitions);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsWrongCompilerDigestWithoutMutatingAnything() {
        when(tasks.findById(taskId)).thenReturn(Optional.of(DataTask.create("check", null, TaskType.SPARK_JAR, null)));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(compiler.compileSparkJarSource(any())).thenReturn(new SparkJarSourceCompilationResponse(
                UUID.randomUUID(), true, 1, "wrong", "jar", new byte[]{1}, List.of()));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.checkOnlineSource(taskId, new SaveSparkJarOnlineSourceRequest("class Job {}")))
                .getStatusCode().value());
        verifyNoInteractions(storage, definitions);
    }
}
