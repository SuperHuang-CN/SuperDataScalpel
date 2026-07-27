package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerControlChannel;

import java.time.Instant;

public record ExecutionLaunch(
        ExecutionIdentity identity,
        String manifestKey,
        String manifestSha256,
        String resultKey,
        String logKey,
        Instant deadlineAt,
        RunnerEventChannel runnerEvent,
        String checkpointUriPrefix,
        RunnerControlChannel runnerControl
) {
    public ExecutionLaunch {
        if (identity == null || manifestKey == null || manifestKey.isBlank() || manifestSha256 == null
                || resultKey == null || logKey == null || runnerEvent == null) {
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
                runnerEvent, null, null);
    }
}
