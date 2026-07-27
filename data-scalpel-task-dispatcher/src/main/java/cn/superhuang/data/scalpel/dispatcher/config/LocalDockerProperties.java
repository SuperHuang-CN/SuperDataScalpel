package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.local-docker")
public record LocalDockerProperties(
        String executable,
        String image,
        String platform,
        String pull,
        String memory,
        String cpus,
        Path runnerJar,
        Path workDirectory,
        Path checkpointDirectory,
        Duration commandTimeout,
        Duration stopTimeout,
        String runnerJavaOptions
) {
    @ConstructorBinding
    public LocalDockerProperties {
        executable = textOrDefault(executable, "docker");
        image = textOrDefault(image, "eclipse-temurin:21-jdk");
        platform = optionalPlatform(platform);
        pull = textOrDefault(pull, "missing").toLowerCase();
        memory = textOrDefault(memory, "4g");
        cpus = textOrDefault(cpus, "2");
        runnerJar = runnerJar == null ? Path.of("lib", "data-scalpel-task-runner.jar") : runnerJar;
        workDirectory = workDirectory == null ? Path.of("work", "task-executions") : workDirectory;
        checkpointDirectory = checkpointDirectory == null
                ? Path.of("work", "task-streaming-checkpoints")
                : checkpointDirectory;
        commandTimeout = commandTimeout == null ? Duration.ofSeconds(30) : commandTimeout;
        stopTimeout = stopTimeout == null ? Duration.ofSeconds(10) : stopTimeout;
        runnerJavaOptions = textOrDefault(runnerJavaOptions, "-Xms512m -Xmx3g");
        if (!pull.equals("always") && !pull.equals("missing") && !pull.equals("never")) {
            throw new IllegalArgumentException("Docker pull 策略只支持 always/missing/never");
        }
        if (commandTimeout.isNegative() || commandTimeout.isZero()
                || stopTimeout.isNegative() || stopTimeout.isZero()) {
            throw new IllegalArgumentException("Docker 命令和停止超时必须大于 0");
        }
    }

    public Path absoluteRunnerJar() { return runnerJar.toAbsolutePath().normalize(); }
    public Path absoluteWorkDirectory() { return workDirectory.toAbsolutePath().normalize(); }
    public Path absoluteCheckpointDirectory() { return checkpointDirectory.toAbsolutePath().normalize(); }

    public LocalDockerProperties(
            String executable,
            String image,
            String platform,
            String pull,
            String memory,
            String cpus,
            Path runnerJar,
            Path workDirectory,
            Duration commandTimeout,
            Duration stopTimeout,
            String runnerJavaOptions
    ) {
        this(executable, image, platform, pull, memory, cpus, runnerJar, workDirectory,
                null, commandTimeout, stopTimeout, runnerJavaOptions);
    }

    private static String textOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String optionalPlatform(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9]+/[a-z0-9][a-z0-9_.-]*(/[a-z0-9][a-z0-9_.-]*)?")) {
            throw new IllegalArgumentException("Docker platform 格式无效，应类似 linux/amd64 或 linux/arm64");
        }
        return normalized;
    }
}
