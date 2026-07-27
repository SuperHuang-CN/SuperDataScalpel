package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherArtifactProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherExecutionStateService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DispatcherResultService {
    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            "MODEL_INPUT",
            "JDBC_INPUT",
            "HTTP_API_INPUT",
            "JOIN",
            "MODEL_OUTPUT",
            "JDBC_OUTPUT"
    );

    private final DispatcherArtifactService artifactService;
    private final DispatcherArtifactProperties properties;
    private final DispatcherTaskResultCodec codec;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherExecutionStateService stateService;

    public DispatcherResultService(
            DispatcherArtifactService artifactService,
            DispatcherArtifactProperties properties,
            DispatcherTaskResultCodec codec,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherExecutionStateService stateService
    ) {
        this.artifactService = artifactService;
        this.properties = properties;
        this.codec = codec;
        this.executionRepository = executionRepository;
        this.stateService = stateService;
    }

    public DispatcherResultResolution reconcile(UUID executionId, String expectedSha256) throws BackendException {
        return resolve(executionId, expectedSha256, true);
    }

    public DispatcherResultResolution verify(UUID executionId, String expectedSha256) throws BackendException {
        return resolve(executionId, expectedSha256, false);
    }

    private DispatcherResultResolution resolve(UUID executionId, String expectedSha256, boolean apply)
            throws BackendException {
        DispatcherTaskExecution snapshot = executionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new BackendException("EXECUTION_NOT_FOUND", "执行账本不存在"));
        Optional<byte[]> content = artifactService.readIfPresent(
                snapshot.getResultKey(), properties.maximumResultBytes());
        if (content.isEmpty()) return DispatcherResultResolution.NOT_FOUND;

        String actualDigest = sha256(content.get());
        if (expectedSha256 != null && !MessageDigest.isEqual(
                actualDigest.getBytes(StandardCharsets.US_ASCII),
                expectedSha256.getBytes(StandardCharsets.US_ASCII))) {
            return DispatcherResultResolution.SIGNAL_REJECTED;
        }

        DispatcherTaskResult result;
        try {
            result = codec.read(content.get());
            validate(snapshot, result);
        } catch (BackendException exception) {
            if (apply) stateService.runnerResultInvalid(executionId, exception.getMessage());
            return DispatcherResultResolution.ARTIFACT_INVALID;
        }
        if (!apply) return DispatcherResultResolution.VERIFIED;
        stateService.applyRunnerResult(executionId, result);
        return DispatcherResultResolution.APPLIED;
    }

    private static void validate(DispatcherTaskExecution execution, DispatcherTaskResult result)
            throws BackendException {
        if (result == null || result.schemaVersion() == null || result.schemaVersion() != 2
                || !execution.getExecutionId().equals(result.executionId())
                || !execution.getRunId().equals(result.runId())
                || result.attempt() == null || execution.getAttempt() != result.attempt()
                || result.state() == null || !result.state().terminal()
                || result.startedAt() == null || result.endedAt() == null
                || result.endedAt().isBefore(result.startedAt())
                || result.durationMs() == null || result.durationMs() < 0
                || result.affectedRows() != null && result.affectedRows() < 0) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner result.json 身份或状态无效");
        }
        if (result.state() == DispatcherTaskResult.State.SUCCESS && result.error() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "成功结果不能包含错误");
        }
        if (result.state() != DispatcherTaskResult.State.SUCCESS
                && result.error() == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "失败结果必须包含安全错误");
        }
        if (result.error() != null) validateError(result.error());

        Set<String> nodeIds = new HashSet<>();
        DispatcherTaskResult.NodeResult failedNode = null;
        for (DispatcherTaskResult.NodeResult node : result.nodeResults()) {
            if (node == null || blank(node.nodeId()) || !nodeIds.add(node.nodeId())
                    || !uuid(node.nodeId()) || blank(node.nodeType()) || blank(node.nodeName())
                    || node.nodeType().length() > 64 || node.nodeName().length() > 200
                    || node.state() == null || node.phase() == null || node.startedAt() == null
                    || node.endedAt() == null || node.endedAt().isBefore(node.startedAt())
                    || node.durationMs() == null || node.durationMs() < 0
                    || node.rowsWritten() != null && node.rowsWritten() < 0 || blank(node.message())
                    || node.message().length() > 1000) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 节点结果字段无效");
            }
            if (!SUPPORTED_NODE_TYPES.contains(node.nodeType())) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 节点类型无效");
            }
            if (node.state() == DispatcherTaskResult.NodeState.SUCCESS && node.error() != null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "成功节点不能包含错误");
            }
            if (node.state() == DispatcherTaskResult.NodeState.FAILED) {
                if (node.error() == null || failedNode != null) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "失败节点结果无效");
                }
                validateError(node.error());
                if (!node.nodeId().equals(node.error().nodeId())
                        || !node.nodeType().equals(node.error().nodeType())
                        || !node.nodeName().equals(node.error().nodeName())
                        || node.phase() != node.error().phase()) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "失败节点与节点错误身份不一致");
                }
                failedNode = node;
            }
        }
        if (result.state() == DispatcherTaskResult.State.SUCCESS && failedNode != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "成功结果不能包含失败节点");
        }
        if (failedNode != null && (result.error() == null
                || !failedNode.error().diagnosticId().equals(result.error().diagnosticId())
                || !failedNode.nodeId().equals(result.error().nodeId()))) {
            throw new BackendException("INVALID_RUNNER_RESULT", "顶层错误与失败节点诊断 ID 不一致");
        }
        if (result.error() != null && result.error().nodeId() != null && failedNode == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "顶层节点错误缺少失败节点结果");
        }
    }

    private static void validateError(DispatcherTaskResult.Error error) throws BackendException {
        if (blank(error.code()) || !error.code().matches("[A-Z][A-Z0-9_]{0,99}")
                || blank(error.message()) || error.message().length() > 1000
                || error.category() == null || error.phase() == null || error.diagnosticId() == null
                || error.sqlState() != null && !error.sqlState().matches("[0-9A-Z]{5}")) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 安全错误字段无效");
        }
        boolean hasNode = error.nodeId() != null;
        if (hasNode != (error.nodeType() != null) || hasNode != (error.nodeName() != null)
                || hasNode && (!uuid(error.nodeId()) || blank(error.nodeType()) || blank(error.nodeName())
                || error.nodeType().length() > 64 || error.nodeName().length() > 200)) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 错误节点身份无效");
        }
    }

    private static boolean uuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(byte[] content) throws BackendException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new BackendException("RESULT_DIGEST_FAILED", "无法校验 Runner result.json", exception);
        }
    }
}
