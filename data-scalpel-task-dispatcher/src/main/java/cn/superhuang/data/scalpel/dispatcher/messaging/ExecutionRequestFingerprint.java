package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ExecutionRequestFingerprint {
    private ExecutionRequestFingerprint() {
    }

    public static String of(SubmitExecutionCommand command) {
        String stable = String.join("\n",
                command.engineId().toString(), command.executionId().toString(), command.runId().toString(),
                Integer.toString(command.attempt()), command.taskId().toString(), command.taskType().name(),
                Integer.toString(command.definitionVersion()), command.deadlineAt().toString(),
                command.artifacts().manifestKey(), command.artifacts().manifestSha256(),
                command.artifacts().resultKey(), command.artifacts().logKey(),
                Integer.toString(command.qualitySampleLimit()),
                command.qualitySampleRuleIds().stream().map(java.util.UUID::toString)
                        .collect(java.util.stream.Collectors.joining(",")),
                command.userJar() == null ? "" : command.userJar().objectKey(),
                command.userJar() == null ? "" : command.userJar().sha256(),
                command.userJar() == null ? "" : Long.toString(command.userJar().sizeBytes()),
                command.sparkConf().stream().map(entry -> entry.name() + "=" + entry.value())
                        .collect(java.util.stream.Collectors.joining("\n")),
                resources(command.executionResources())
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stable.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    public static String of(StartStreamingExecutionCommand command) {
        String stable = String.join("\n",
                command.engineId().toString(), command.executionId().toString(), command.runId().toString(),
                Integer.toString(command.attempt()), command.taskId().toString(), command.deploymentId().toString(),
                Integer.toString(command.definitionVersion()), command.checkpointKeyPrefix(), command.taskType().name(),
                command.artifacts().manifestKey(), command.artifacts().manifestSha256(),
                command.artifacts().resultKey(), command.artifacts().logKey(),
                command.userJar() == null ? "" : command.userJar().objectKey(),
                command.userJar() == null ? "" : command.userJar().sha256(),
                command.userJar() == null ? "" : Long.toString(command.userJar().sizeBytes()),
                command.sparkConf().stream().map(entry -> entry.name() + "=" + entry.value())
                        .collect(java.util.stream.Collectors.joining("\n")),
                resources(command.executionResources())
        );
        return sha256(stable);
    }

    private static String sha256(String stable) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(stable.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    private static String resources(cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec value) {
        return value == null ? "" : "%d,%d,%d,%d,%d".formatted(value.driverCores(), value.driverMemoryMiB(),
                value.executorInstances(), value.executorCores(), value.executorMemoryMiB());
    }
}
