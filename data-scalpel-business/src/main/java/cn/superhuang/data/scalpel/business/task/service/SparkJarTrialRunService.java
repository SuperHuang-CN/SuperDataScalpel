package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.web.request.SaveSparkJarOnlineSourceRequest;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarOnlineCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarOnlineSourceResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTrialRunResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationResponse;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SparkJarTrialRunService {
    private static final int MAXIMUM_TRIAL_JAR_BYTES = 5 * 1024 * 1024;

    private final SparkJarTaskDefinitionService definitionService;
    private final TaskCompilationService compilationService;
    private final TaskRunService taskRunService;
    private final TaskStreamingService taskStreamingService;

    public SparkJarTrialRunService(
            SparkJarTaskDefinitionService definitionService,
            TaskCompilationService compilationService,
            TaskRunService taskRunService,
            TaskStreamingService taskStreamingService
    ) {
        this.definitionService = definitionService;
        this.compilationService = compilationService;
        this.taskRunService = taskRunService;
        this.taskStreamingService = taskStreamingService;
    }

    public SparkJarTrialRunResponse run(UUID taskId, SaveSparkJarOnlineSourceRequest request) {
        SparkJarOnlineSourceResponse saved = definitionService.saveOnlineSource(taskId, request);
        SparkJarJobMode jobMode = definitionService.savedJobMode(taskId);
        SparkJarSourceCompilationResponse compilation = compilationService.compileSparkJarSource(
                new SparkJarSourceCompilationRequest(UUID.randomUUID(), saved.sourceCode(), jobMode));
        if (!saved.sourceSha256().equals(compilation.sourceSha256())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Task Engine 返回的源码摘要不一致");
        }
        List<SparkJarOnlineCompilationResponse.Diagnostic> diagnostics = compilation.diagnostics().stream()
                .map(value -> new SparkJarOnlineCompilationResponse.Diagnostic(
                        SparkJarOnlineCompilationResponse.Severity.valueOf(value.severity().name()),
                        value.code(), value.message(), value.line(), value.column(), value.endLine(), value.endColumn()))
                .toList();
        if (!compilation.successful()) {
            return new SparkJarTrialRunResponse(SparkJarTrialRunResponse.Status.COMPILE_FAILED,
                    compilation.durationMs(), definitionService.getOnlineSource(taskId), diagnostics, null);
        }
        byte[] jar = compilation.jarBytes();
        if (jar == null || jar.length == 0 || jar.length > MAXIMUM_TRIAL_JAR_BYTES
                || !Objects.equals(compilation.jarSha256(), sha256(jar))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Task Engine 返回的试运行编译制品无效");
        }
        definitionService.validateOnlineTrialJar(taskId, jar);
        TaskRunResponse run = jobMode == SparkJarJobMode.STREAMING
                ? taskStreamingService.submitStreamingJarTrial(
                        taskId, saved.sourceSha256(), jar, compilation.jarSha256())
                : taskRunService.submitSparkJarTrial(
                        taskId, saved.sourceSha256(), jar, compilation.jarSha256());
        return new SparkJarTrialRunResponse(SparkJarTrialRunResponse.Status.QUEUED,
                compilation.durationMs(), definitionService.getOnlineSource(taskId), diagnostics, run);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
