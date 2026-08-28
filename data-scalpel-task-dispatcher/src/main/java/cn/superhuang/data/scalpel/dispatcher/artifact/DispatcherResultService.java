package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import java.math.BigDecimal;
import java.math.RoundingMode;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;

@Service
public class DispatcherResultService {
    private static final Set<String> SUPPORTED_NODE_TYPES = Arrays.stream(CanvasNodeType.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> ORDINARY_OUTPUT_NODE_TYPES = Set.of(
            "JDBC_OUTPUT", "MODEL_OUTPUT", "FILE_OUTPUT", "KAFKA_OUTPUT");
    private static final Set<String> SNAPSHOT_OUTPUT_NODE_TYPES = Set.of(
            "JDBC_SNAPSHOT_SYNC_OUTPUT", "MODEL_SNAPSHOT_SYNC_OUTPUT");

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
            result = degradeInvalidSparkJarLineage(result);
            validate(snapshot, result);
        } catch (BackendException exception) {
            if (apply) stateService.runnerResultInvalid(executionId, exception.getMessage());
            return DispatcherResultResolution.ARTIFACT_INVALID;
        }
        if (!apply) return DispatcherResultResolution.VERIFIED;
        stateService.applyRunnerResult(executionId, result, actualDigest);
        return DispatcherResultResolution.APPLIED;
    }

    private static DispatcherTaskResult degradeInvalidSparkJarLineage(
            DispatcherTaskResult result
    ) {
        if (result == null || result.lineage() == null
                || result.schemaVersion() == null || result.schemaVersion() < 8
                || result.taskType() != ExecutionTaskType.SPARK_JAR) {
            return result;
        }
        try {
            validateLineage(result.lineage());
            return result;
        } catch (BackendException ignored) {
            TaskLineageEvidence unavailable = TaskLineageEvidence.unavailable(
                    "LINEAGE_RESULT_INVALID", "运行血缘结果无效，已降级且未影响用户作业");
            return new DispatcherTaskResult(
                    result.schemaVersion(), result.executionId(), result.runId(), result.attempt(),
                    result.state(), result.startedAt(), result.endedAt(), result.durationMs(),
                    result.affectedRows(), result.nodeResults(), result.taskType(),
                    result.qualityResult(), result.userJobObservability(), unavailable, result.error());
        }
    }

    private void validate(DispatcherTaskExecution execution, DispatcherTaskResult result)
            throws BackendException {
        if (result == null || !DispatcherTaskResult.supportsSchemaVersion(result.schemaVersion())
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

        if (result.schemaVersion() >= 4 && result.taskType() == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner v4 结果缺少任务类型");
        }
        if (result.schemaVersion() < 6 && result.userJobObservability() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner v2～v5 结果不能包含用户作业观测载荷");
        }
        ExecutionTaskType resultType = result.taskType() == null
                ? ExecutionTaskType.SPARK_CANVAS : result.taskType();
        if (resultType != execution.getTaskType()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 结果任务类型与执行账本不一致");
        }
        if (result.schemaVersion() < 8 && result.lineage() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner v2～v7 结果不能包含运行血缘");
        }
        if (result.lineage() != null && resultType != ExecutionTaskType.SPARK_JAR
                || result.schemaVersion() >= 8 && resultType == ExecutionTaskType.SPARK_JAR
                && result.state() == DispatcherTaskResult.State.SUCCESS && result.lineage() == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner v8 Spark JAR 血缘载荷无效");
        }
        if (result.lineage() != null) validateLineage(result.lineage());
        if (resultType == ExecutionTaskType.SPARK_MODEL_QUALITY) {
            if (result.userJobObservability() != null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "模型质检结果不能包含用户作业观测载荷");
            }
            if (result.state() == DispatcherTaskResult.State.SUCCESS) {
                validateQuality(execution, result);
            } else {
                validateQualityFailure(execution, result);
            }
            return;
        }
        if (result.qualityResult() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "非质检结果不能包含质量结果");
        }
        if (resultType == ExecutionTaskType.SPARK_JAR && !result.nodeResults().isEmpty()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Spark JAR 结果不能包含 Canvas 节点结果");
        }
        if (result.userJobObservability() != null && resultType != ExecutionTaskType.SPARK_JAR) {
            throw new BackendException("INVALID_RUNNER_RESULT", "用户作业观测载荷只能属于 Spark JAR 批任务");
        }

        Set<UUID> nodeIds = new HashSet<>();
        DispatcherTaskResult.NodeResult failedNode = null;
        boolean outputNodeSeen = false;
        boolean outputRowsUnknown = false;
        long outputRows = 0L;
        for (DispatcherTaskResult.NodeResult node : result.nodeResults()) {
            if (node == null || blank(node.nodeId()) || !uuid(node.nodeId())
                    || !nodeIds.add(UUID.fromString(node.nodeId()))
                    || blank(node.nodeType()) || blank(node.nodeName())
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
            if (ORDINARY_OUTPUT_NODE_TYPES.contains(node.nodeType())
                    || SNAPSHOT_OUTPUT_NODE_TYPES.contains(node.nodeType())) {
                outputNodeSeen = true;
                if (node.rowsWritten() == null) {
                    if (node.state() == DispatcherTaskResult.NodeState.SUCCESS
                            || ORDINARY_OUTPUT_NODE_TYPES.contains(node.nodeType())) {
                        outputRowsUnknown = true;
                    }
                } else if (!outputRowsUnknown) {
                    try {
                        outputRows = Math.addExact(outputRows, node.rowsWritten());
                    } catch (ArithmeticException exception) {
                        outputRowsUnknown = true;
                    }
                }
            }
            if (node.state() == DispatcherTaskResult.NodeState.SUCCESS && node.error() != null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "成功节点不能包含错误");
            }
            validateMetrics(result.schemaVersion(), node);
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
        if (result.schemaVersion() >= 7 && failedNode != null
                && (!failedNode.error().code().equals(result.error().code())
                || !failedNode.nodeType().equals(result.error().nodeType())
                || !failedNode.nodeName().equals(result.error().nodeName())
                || failedNode.phase() != result.error().phase())) {
            throw new BackendException("INVALID_RUNNER_RESULT", "顶层错误与失败节点错误不一致");
        }
        if (result.error() != null && result.error().nodeId() != null && failedNode == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "顶层节点错误缺少失败节点结果");
        }
        if (result.schemaVersion() >= 7 && outputNodeSeen
                && (outputRowsUnknown && result.affectedRows() != null
                || !outputRowsUnknown && !Long.valueOf(outputRows).equals(result.affectedRows()))) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 总影响行数与输出节点不一致");
        }
    }

    private static void validateLineage(TaskLineageEvidence evidence) throws BackendException {
        if (evidence.analysisStatus() == null || evidence.flows().size() > 100
                || evidence.warnings().size() > 2_000
                || evidence.analysisStatus() == TaskLineageEvidence.AnalysisStatus.UNAVAILABLE
                && (!evidence.flows().isEmpty() || evidence.coverage() != null)
                || evidence.analysisStatus() != TaskLineageEvidence.AnalysisStatus.UNAVAILABLE
                && (evidence.flows().isEmpty() || evidence.coverage() == null)) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘汇总无效");
        }
        long assets = 0;
        long fields = 0;
        long edges = 0;
        long usages = 0;
        Set<String> flowKeys = new HashSet<>();
        for (TaskLineageEvidence.Flow flow : evidence.flows()) {
            if (flow == null || blank(flow.flowKey()) || flow.flowKey().length() > 128
                    || !flowKeys.add(flow.flowKey()) || blank(flow.producerKey())
                    || flow.producerKey().length() > 128 || blank(flow.producerType())
                    || flow.producerType().length() > 64 || flow.coverage() == null
                    || flow.outputAsset() == null || flow.outputAsset().role() != TaskLineageEvidence.AssetRole.OUTPUT) {
                throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘 Flow 无效");
            }
            assets += 1L + flow.inputAssets().size();
            fields += flow.fields().size();
            edges += flow.fieldEdges().size();
            usages += flow.fieldUsages().size();
            validateLineageAsset(flow.outputAsset());
            for (TaskLineageEvidence.Asset asset : flow.inputAssets()) {
                if (asset == null || asset.role() != TaskLineageEvidence.AssetRole.INPUT) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘输入资产无效");
                }
                validateLineageAsset(asset);
            }
            for (TaskLineageEvidence.Field field : flow.fields()) {
                if (field == null || blank(field.localAssetKey()) || field.localAssetKey().length() > 128
                        || blank(field.localFieldKey()) || field.localFieldKey().length() > 128
                        || blank(field.columnCode()) || field.columnCode().length() > 128
                        || field.columnName() != null && field.columnName().length() > 200
                        || field.ordinal() < 0) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘字段无效");
                }
            }
            for (TaskLineageEvidence.FieldEdge edge : flow.fieldEdges()) {
                if (edge == null || edge.source() == null || edge.target() == null
                        || blank(edge.derivationKey()) || edge.derivationKey().length() > 128
                        || edge.derivationType() == null || blank(edge.transformNodeKey())
                        || edge.transformNodeKey().length() > 128) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘字段边无效");
                }
                validateReference(edge.source());
                validateReference(edge.target());
            }
            for (TaskLineageEvidence.FieldUsage usage : flow.fieldUsages()) {
                if (usage == null || usage.field() == null || blank(usage.nodeKey())
                        || usage.nodeKey().length() > 128 || usage.usageType() == null) {
                    throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘字段用途无效");
                }
                validateReference(usage.field());
            }
            for (TaskLineageEvidence.Warning warning : flow.warnings()) validateLineageWarning(warning);
        }
        for (TaskLineageEvidence.Warning warning : evidence.warnings()) validateLineageWarning(warning);
        if (assets > 2_000 || fields > 20_000 || edges > 50_000 || usages > 50_000) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘超过结果安全上限");
        }
    }

    private static void validateLineageAsset(TaskLineageEvidence.Asset asset) throws BackendException {
        if (asset.kind() == null || blank(asset.localAssetKey()) || asset.localAssetKey().length() > 128
                || blank(asset.safeDisplayName()) || asset.safeDisplayName().length() > 255
                || asset.role() == TaskLineageEvidence.AssetRole.OUTPUT && asset.writeMode() == null
                || asset.role() == TaskLineageEvidence.AssetRole.INPUT && asset.writeMode() != null
                || asset.kind() == TaskLineageEvidence.AssetKind.MODEL
                && (asset.modelId() == null || asset.modelSchemaVersion() == null || asset.modelSchemaVersion() < 1)
                || asset.kind() == TaskLineageEvidence.AssetKind.JDBC_TABLE
                && (asset.dataSourceId() == null || blank(asset.physicalTableName()))
                || asset.kind() == TaskLineageEvidence.AssetKind.EXTERNAL_RESOURCE
                && (asset.externalResourceType() == null || blank(asset.resourceKeyHash()))) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘资产无效");
        }
        if (asset.catalogName() != null && asset.catalogName().length() > 128
                || asset.schemaName() != null && asset.schemaName().length() > 128
                || asset.physicalTableName() != null && asset.physicalTableName().length() > 128
                || asset.resourceKeyHash() != null && asset.resourceKeyHash().length() > 128) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘资产标识过长");
        }
    }

    private static void validateReference(TaskLineageEvidence.FieldReference reference) throws BackendException {
        if (blank(reference.localAssetKey()) || reference.localAssetKey().length() > 128
                || blank(reference.localFieldKey()) || reference.localFieldKey().length() > 128) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘字段引用无效");
        }
    }

    private static void validateLineageWarning(TaskLineageEvidence.Warning warning) throws BackendException {
        if (warning == null || blank(warning.code()) || warning.code().length() > 100
                || blank(warning.message()) || warning.message().length() > 1_000
                || warning.producerKey() != null && warning.producerKey().length() > 128
                || warning.flowKey() != null && warning.flowKey().length() > 128
                || warning.outputOrdinal() != null && warning.outputOrdinal() < 1) {
            throw new BackendException("INVALID_RUNNER_RESULT", "运行血缘警告无效");
        }
    }

    private void validateQuality(DispatcherTaskExecution execution, DispatcherTaskResult result) throws BackendException {
        DispatcherTaskResult.QualityResult quality = result.qualityResult();
        if (result.schemaVersion() < 4
                || result.state() != DispatcherTaskResult.State.SUCCESS
                || quality == null || !result.nodeResults().isEmpty() || result.affectedRows() != null
                || quality.conclusion() == null || quality.technicalFailure() != null
                || quality.totalRules() == null || quality.passedRules() == null
                || quality.failedRules() == null || quality.skippedRules() == null
                || quality.checkedRows() == null || quality.totalRules() < 1
                || quality.passedRules() < 0 || quality.failedRules() < 0 || quality.skippedRules() < 0
                || quality.checkedRows() < 0
                || quality.totalRules() != quality.passedRules() + quality.failedRules() + quality.skippedRules()
                || quality.ruleResults().size() != quality.passedRules() + quality.failedRules()
                || quality.skippedRuleResults().size() != quality.skippedRules()
                || quality.passedRules() + quality.failedRules() == 0
                || quality.conclusion() == cn.superhuang.data.scalpel.contract.quality.QualityConclusion.PASSED
                && quality.failedRules() != 0
                || quality.conclusion() == cn.superhuang.data.scalpel.contract.quality.QualityConclusion.FAILED
                && quality.failedRules() == 0) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量结果汇总无效");
        }
        Set<UUID> ruleIds = new HashSet<>();
        long passedRules = 0;
        long failedRules = 0;
        long totalSampleBytes = 0;
        for (DispatcherTaskResult.QualityRuleResult rule : quality.ruleResults()) {
            if (rule == null || rule.ruleId() == null || !ruleIds.add(rule.ruleId())
                    || blank(rule.ruleName()) || rule.ruleType() == null || rule.severity() == null
                    || rule.ruleName().length() > 100 || rule.state() == null
                    || rule.durationMs() < 0 || rule.metric() == null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量规则结果字段无效");
            }
            validateQualityMetric(rule, quality.checkedRows());
            totalSampleBytes += validateQualitySample(execution, result, rule);
            if (rule.state() == DispatcherTaskResult.QualityRuleState.PASSED) passedRules++;
            else failedRules++;
        }
        if (passedRules != quality.passedRules() || failedRules != quality.failedRules()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量规则状态与汇总不一致");
        }
        if (totalSampleBytes > 100L * 1024 * 1024) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检样本总大小超过安全上限");
        }
        for (cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.SkippedQualityRuleSnapshot skipped
                : quality.skippedRuleResults()) {
            if (skipped == null || skipped.id() == null || !ruleIds.add(skipped.id())
                    || blank(skipped.name()) || skipped.name().length() > 100 || skipped.type() == null
                    || blank(skipped.reasonCode()) || !skipped.reasonCode().matches("[A-Z][A-Z0-9_]{0,99}")
                    || blank(skipped.reason()) || skipped.reason().length() > 1000) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量跳过规则字段无效");
            }
        }
    }

    private void validateQualityFailure(DispatcherTaskExecution execution, DispatcherTaskResult result)
            throws BackendException {
        DispatcherTaskResult.QualityResult quality = result.qualityResult();
        if (result.schemaVersion() < 4
                || quality == null || !result.nodeResults().isEmpty()
                || result.affectedRows() != null || quality.conclusion() != null
                || quality.totalRules() != null || quality.passedRules() != null
                || quality.failedRules() != null || quality.skippedRules() != null
                || quality.checkedRows() != null && quality.checkedRows() < 0) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检技术失败结果无效");
        }
        Set<UUID> ruleIds = new HashSet<>();
        long totalSampleBytes = 0;
        for (DispatcherTaskResult.QualityRuleResult rule : quality.ruleResults()) {
            if (rule == null || rule.ruleId() == null || !ruleIds.add(rule.ruleId())
                    || blank(rule.ruleName()) || rule.ruleName().length() > 100 || rule.ruleType() == null
                    || rule.severity() == null || rule.state() == null || rule.durationMs() < 0
                    || rule.metric() == null || quality.checkedRows() == null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 已完成质量规则结果无效");
            }
            validateQualityMetric(rule, quality.checkedRows());
            if (result.schemaVersion() >= 5 && rule.sample() == null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 已完成规则缺少样本状态");
            }
            totalSampleBytes += validateQualitySample(execution, result, rule);
        }
        for (cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.SkippedQualityRuleSnapshot skipped
                : quality.skippedRuleResults()) {
            if (skipped == null || skipped.id() == null || !ruleIds.add(skipped.id())
                    || blank(skipped.name()) || skipped.name().length() > 100 || skipped.type() == null
                    || blank(skipped.reasonCode()) || !skipped.reasonCode().matches("[A-Z][A-Z0-9_]{0,99}")
                    || blank(skipped.reason()) || skipped.reason().length() > 1000) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量跳过规则字段无效");
            }
        }
        DispatcherTaskResult.QualityRuleTechnicalFailure failure = quality.technicalFailure();
        if (!quality.ruleResults().isEmpty() && failure == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 已完成规则缺少质检失败规则上下文");
        }
        if (failure != null && (failure.ruleId() == null || !ruleIds.add(failure.ruleId())
                || blank(failure.ruleName()) || failure.ruleName().length() > 100
                || failure.ruleType() == null || failure.severity() == null || failure.durationMs() < 0
                || failure.diagnosticId() == null || result.error() == null
                || !failure.diagnosticId().equals(result.error().diagnosticId()))) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检失败规则上下文无效");
        }
        if (totalSampleBytes > 100L * 1024 * 1024) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检样本总大小超过安全上限");
        }
    }

    private long validateQualitySample(
            DispatcherTaskExecution execution,
            DispatcherTaskResult result,
            DispatcherTaskResult.QualityRuleResult rule
    ) throws BackendException {
        if (result.schemaVersion() == 4) {
            if (rule.sample() != null) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner v4 质量规则不能包含样本结果");
            }
            return 0;
        }
        DispatcherTaskResult.QualitySample sample = rule.sample();
        if (sample == null || sample.status() == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner v5 质量规则缺少样本状态");
        }
        boolean rowLevel = rule.ruleType() != ModelQualityRuleType.ROW_COUNT
                && rule.ruleType() != ModelQualityRuleType.FRESHNESS;
        boolean requested = execution.getQualitySampleRuleIds().contains(rule.ruleId());
        if (rowLevel && execution.getQualitySampleLimit() > 0 && !requested) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检规则不在失败样本执行清单中");
        }
        if (rule.state() == DispatcherTaskResult.QualityRuleState.PASSED) {
            requireEmptySample(sample, DispatcherTaskResult.QualitySampleStatus.NOT_FAILED);
            return 0;
        }
        if (!rowLevel) {
            requireEmptySample(sample, DispatcherTaskResult.QualitySampleStatus.NOT_APPLICABLE);
            return 0;
        }
        if (!requested) {
            requireEmptySample(sample, DispatcherTaskResult.QualitySampleStatus.DISABLED);
            return 0;
        }
        if (sample.status() != DispatcherTaskResult.QualitySampleStatus.AVAILABLE
                || sample.sampledRows() == null || sample.sampledRows() < 1
                || sample.sampledRows() > execution.getQualitySampleLimit()
                || sample.violationRows() == null || sample.violationRows() < sample.sampledRows()
                || sample.sampledRows() != Math.min(sample.violationRows(), execution.getQualitySampleLimit())
                || !(rule.metric() instanceof DispatcherTaskResult.ViolationMetricResult metric)
                || metric.violationCount() != sample.violationRows()
                || sample.truncated() == null || sample.truncated() != (sample.sampledRows() < sample.violationRows())
                || sample.sizeBytes() == null || sample.sizeBytes() < 8
                || sample.sizeBytes() > 20L * 1024 * 1024
                || sample.sha256() == null || !sample.sha256().matches("[0-9a-f]{64}")
                || sample.rowLocatable() == null || sample.columns().isEmpty()
                || sample.columns().stream().anyMatch(column -> column == null || blank(column.code())
                || column.code().length() > 200 || blank(column.name()) || column.name().length() > 200
                || column.type() == null || column.diagnostic() != (column.fieldId() == null)
                || column.diagnostic() && column.primaryKey())
                || sample.columns().stream().map(DispatcherTaskResult.QualitySampleColumn::code).distinct().count()
                != sample.columns().size()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检样本描述无效");
        }
        String objectKey = "task-runs/%s/attempts/%d/quality/samples/%s.parquet".formatted(
                execution.getRunId(), execution.getAttempt(), rule.ruleId());
        byte[] content = artifactService.readIfPresent(objectKey, 20 * 1024 * 1024)
                .orElseThrow(() -> new BackendException("QUALITY_SAMPLE_MISSING", "质检样本制品不存在"));
        if (content.length != sample.sizeBytes() || content.length < 8
                || content[0] != 'P' || content[1] != 'A' || content[2] != 'R' || content[3] != '1'
                || content[content.length - 4] != 'P' || content[content.length - 3] != 'A'
                || content[content.length - 2] != 'R' || content[content.length - 1] != '1'
                || !MessageDigest.isEqual(sha256(content).getBytes(StandardCharsets.US_ASCII),
                sample.sha256().getBytes(StandardCharsets.US_ASCII))) {
            throw new BackendException("QUALITY_SAMPLE_INVALID", "质检样本制品完整性校验失败");
        }
        return content.length;
    }

    private static void requireEmptySample(
            DispatcherTaskResult.QualitySample sample,
            DispatcherTaskResult.QualitySampleStatus expected
    ) throws BackendException {
        if (sample.status() != expected || sample.sampledRows() != null || sample.violationRows() != null
                || sample.truncated() != null || sample.sizeBytes() != null || sample.sha256() != null
                || sample.rowLocatable() != null || !sample.columns().isEmpty()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质检样本状态与规则结果不一致");
        }
    }

    private static void validateQualityMetric(
            DispatcherTaskResult.QualityRuleResult rule,
            long checkedRows
    ) throws BackendException {
        boolean passed;
        if (rule.metric() instanceof DispatcherTaskResult.ViolationMetricResult metric) {
            if (rule.ruleType() == ModelQualityRuleType.ROW_COUNT
                    || rule.ruleType() == ModelQualityRuleType.FRESHNESS
                    || metric.violationCount() < 0 || metric.violationCount() > checkedRows
                    || metric.violationPercent() == null || metric.violationPercent().signum() < 0
                    || metric.violationPercent().compareTo(BigDecimal.valueOf(100)) > 0
                    || metric.toleranceMetric() == null || metric.toleranceValue() == null
                    || metric.toleranceValue().signum() < 0
                    || metric.toleranceMetric() == ViolationMetric.COUNT
                    && metric.toleranceValue().stripTrailingZeros().scale() > 0
                    || metric.toleranceMetric() == ViolationMetric.PERCENT
                    && metric.toleranceValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量异常指标无效");
            }
            BigDecimal expectedPercent = checkedRows == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(metric.violationCount()).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(checkedRows), 6, RoundingMode.HALF_UP).stripTrailingZeros();
            if (metric.violationPercent().compareTo(expectedPercent) != 0) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量异常比例与行数不一致");
            }
            passed = metric.toleranceMetric() == ViolationMetric.COUNT
                    ? BigDecimal.valueOf(metric.violationCount()).compareTo(metric.toleranceValue()) <= 0
                    : BigDecimal.valueOf(metric.violationCount()).multiply(BigDecimal.valueOf(100))
                    .compareTo(metric.toleranceValue().multiply(BigDecimal.valueOf(checkedRows))) <= 0;
        } else if (rule.metric() instanceof DispatcherTaskResult.RowCountMetricResult metric) {
            if (rule.ruleType() != ModelQualityRuleType.ROW_COUNT
                    || metric.actualRows() != checkedRows || metric.minimumRows() < 1) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 行数质量指标无效");
            }
            passed = metric.actualRows() >= metric.minimumRows();
        } else if (rule.metric() instanceof DispatcherTaskResult.FreshnessMetricResult metric) {
            if (rule.ruleType() != ModelQualityRuleType.FRESHNESS || metric.maximumDelayMinutes() < 1
                    || metric.actualDelayMinutes() != null && metric.actualDelayMinutes() < 0
                    || (metric.maximumValue() == null) != (metric.actualDelayMinutes() == null)) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 新鲜度质量指标无效");
            }
            passed = metric.maximumValue() != null
                    && metric.actualDelayMinutes() <= metric.maximumDelayMinutes();
        } else {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量指标类型无效");
        }
        if (passed != (rule.state() == DispatcherTaskResult.QualityRuleState.PASSED)) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 质量规则状态与指标不一致");
        }
    }

    private static void validateMetrics(
            int schemaVersion,
            DispatcherTaskResult.NodeResult node
    ) throws BackendException {
        boolean snapshotNode = SNAPSHOT_OUTPUT_NODE_TYPES.contains(node.nodeType());
        boolean ordinaryOutputNode = ORDINARY_OUTPUT_NODE_TYPES.contains(node.nodeType());
        if (schemaVersion == 2 && node.metrics() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "result.json v2 不能包含节点指标");
        }
        if (schemaVersion >= 3 && node.state() == DispatcherTaskResult.NodeState.SUCCESS
                && snapshotNode && node.metrics() == null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "快照同步成功节点必须包含指标");
        }
        if (node.metrics() instanceof DispatcherTaskResult.SnapshotSyncMetrics metrics) {
            if (!snapshotNode || node.state() == DispatcherTaskResult.NodeState.FAILED
                    || metrics.sourceRows() < 0 || metrics.targetRows() < 0
                    || metrics.insertedRows() < 0 || metrics.updatedRows() < 0
                    || metrics.deletedRows() < 0 || metrics.unchangedRows() < 0
                    || metrics.retainedTargetOnlyRows() < 0
                    || metrics.sourceRows() != metrics.insertedRows()
                    + metrics.updatedRows() + metrics.unchangedRows()
                    || metrics.targetRows() != metrics.deletedRows()
                    + metrics.retainedTargetOnlyRows() + metrics.updatedRows() + metrics.unchangedRows()
                    || node.rowsWritten() == null
                    || node.rowsWritten().longValue() != metrics.rowsWritten()) {
                throw new BackendException("INVALID_RUNNER_RESULT", "快照同步节点指标不一致");
            }
            return;
        }
        if (node.metrics() instanceof DispatcherTaskResult.OutputWritesMetrics metrics) {
            validateOutputWritesMetrics(schemaVersion, node, ordinaryOutputNode, metrics);
            return;
        }
        if (node.metrics() != null) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 节点指标类型无效");
        }
        if (node.state() == DispatcherTaskResult.NodeState.FAILED && snapshotNode) return;
    }

    private static void validateOutputWritesMetrics(
            int schemaVersion,
            DispatcherTaskResult.NodeResult node,
            boolean ordinaryOutputNode,
            DispatcherTaskResult.OutputWritesMetrics metrics
    ) throws BackendException {
        if (schemaVersion != 7 || !ordinaryOutputNode || metrics.writes().isEmpty()) {
            throw new BackendException("INVALID_RUNNER_RESULT", "逐写入指标所属节点或版本无效");
        }
        Set<UUID> writeIds = new HashSet<>();
        boolean failed = false;
        Long rowsWritten = 0L;
        String failedCode = null;
        for (DispatcherTaskResult.OutputWriteResult write : metrics.writes()) {
            if (write == null || !uuid(write.writeId())
                    || !writeIds.add(UUID.fromString(write.writeId()))
                    || blank(write.sourceTableName()) || write.sourceTableName().length() > 256
                    || blank(write.targetDisplayName()) || write.targetDisplayName().length() > 1024
                    || write.state() == null || write.affectedRows() != null && write.affectedRows() < 0
                    || write.errorCode() != null
                    && !write.errorCode().matches("[A-Z][A-Z0-9_]{0,99}")) {
                throw new BackendException("INVALID_RUNNER_RESULT", "Runner 逐写入结果字段无效");
            }
            switch (write.state()) {
                case PENDING, RUNNING -> throw new BackendException(
                        "INVALID_RUNNER_RESULT", "终态结果不能包含未完成写入");
                case SUCCESS -> {
                    if (failed || write.errorCode() != null) {
                        throw new BackendException("INVALID_RUNNER_RESULT", "Runner 逐写入顺序无效");
                    }
                    if (write.affectedRows() == null || rowsWritten == null) {
                        rowsWritten = null;
                    } else {
                        try {
                            rowsWritten = Math.addExact(rowsWritten, write.affectedRows());
                        } catch (ArithmeticException exception) {
                            rowsWritten = null;
                        }
                    }
                }
                case FAILED -> {
                    if (failed || write.affectedRows() != null || blank(write.errorCode())) {
                        throw new BackendException("INVALID_RUNNER_RESULT", "Runner 失败写入结果无效");
                    }
                    failed = true;
                    failedCode = write.errorCode();
                }
                case SKIPPED -> {
                    if (!failed || write.affectedRows() != null || write.errorCode() != null) {
                        throw new BackendException("INVALID_RUNNER_RESULT", "Runner 跳过写入结果无效");
                    }
                }
            }
        }
        if (!java.util.Objects.equals(rowsWritten, node.rowsWritten())
                || node.state() == DispatcherTaskResult.NodeState.SUCCESS && failed
                || node.state() == DispatcherTaskResult.NodeState.FAILED && !failed
                || failed && (node.error() == null || !failedCode.equals(node.error().code()))) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner 逐写入指标与节点状态不一致");
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
