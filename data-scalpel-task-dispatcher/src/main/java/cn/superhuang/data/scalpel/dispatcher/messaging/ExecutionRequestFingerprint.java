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
                command.artifacts().resultKey(), command.artifacts().logKey()
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
                Integer.toString(command.definitionVersion()),
                command.artifacts().manifestKey(), command.artifacts().manifestSha256(),
                command.artifacts().resultKey(), command.artifacts().logKey()
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
}
