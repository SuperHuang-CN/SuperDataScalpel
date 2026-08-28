package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerControlChannel;
import cn.superhuang.data.scalpel.contract.execution.ExecutionUserJarArtifact;
import cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecutionLaunch(
        ExecutionIdentity identity,
        String manifestKey,
        String manifestSha256,
        String resultKey,
        String logKey,
        Instant deadlineAt,
        RunnerEventChannel runnerEvent,
        String checkpointUriPrefix,
        RunnerControlChannel runnerControl,
        List<UUID> qualitySampleRuleIds,
        ExecutionUserJarArtifact userJar,
        List<SparkConfigurationEntry> sparkConf,
        SparkExecutionResourceSpec executionResources
) {
    public ExecutionLaunch {
        qualitySampleRuleIds = qualitySampleRuleIds == null ? List.of() : List.copyOf(qualitySampleRuleIds);
        sparkConf = sparkConf == null ? List.of() : List.copyOf(sparkConf);
        if (identity == null || manifestKey == null || manifestKey.isBlank() || manifestSha256 == null
                || resultKey == null || logKey == null || runnerEvent == null || executionResources == null) {
            throw new IllegalArgumentException("执行启动参数无效");
        }
    }

    public ExecutionLaunch(
            ExecutionIdentity identity,
            String manifestKey,
            String manifestSha256,
            String resultKey,
            String logKey,
            Instant deadlineAt,
            RunnerEventChannel runnerEvent
    ) {
        this(identity, manifestKey, manifestSha256, resultKey, logKey, deadlineAt,
                runnerEvent, null, null, List.of(), null, List.of(), defaultResources());
    }

    public ExecutionLaunch(
            ExecutionIdentity identity, String manifestKey, String manifestSha256,
            String resultKey, String logKey, Instant deadlineAt, RunnerEventChannel runnerEvent,
            String checkpointUriPrefix, RunnerControlChannel runnerControl, List<UUID> qualitySampleRuleIds
    ) {
        this(identity, manifestKey, manifestSha256, resultKey, logKey, deadlineAt, runnerEvent,
                checkpointUriPrefix, runnerControl, qualitySampleRuleIds, null, List.of(), defaultResources());
    }

    private static SparkExecutionResourceSpec defaultResources() {
        return new SparkExecutionResourceSpec(2, 4096, 1, 1, 1024);
    }
}
