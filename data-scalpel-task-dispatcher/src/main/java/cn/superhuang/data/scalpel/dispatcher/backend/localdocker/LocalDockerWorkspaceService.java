package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactDownload;
import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.data.scalpel.dispatcher.artifact.ArtifactLaunchAccess;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.DriverJavaOptions;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

@Component
public class LocalDockerWorkspaceService {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final LocalDockerProperties properties;
    private final ObjectMapper objectMapper;

    public LocalDockerWorkspaceService(LocalDockerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public LocalDockerWorkspace create(ExecutionLaunch launch, ArtifactLaunchAccess access) throws BackendException {
        try {
            Path root = properties.absoluteWorkDirectory();
            Files.createDirectories(root);
            secureDirectory(root);
            Files.createDirectories(properties.absoluteCheckpointDirectory());
            secureDirectory(properties.absoluteCheckpointDirectory());
            Path realRoot = root.toRealPath();
            Path directory = realRoot.resolve(launch.identity().executionId().toString())
                    .resolve("attempt-" + launch.identity().attempt()).normalize();
            if (!directory.startsWith(realRoot)) throw new IOException("执行工作目录越界");
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
                throw new IOException("执行工作目录不能是符号链接");
            }
            Files.createDirectories(directory);
            secureDirectory(directory);
            Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(realRoot)) throw new IOException("执行工作目录越界");

            Path launchFile = realDirectory.resolve("launch.json");
            Path environmentFile = realDirectory.resolve("runner.env");
            TaskExecutionLaunchDescriptor descriptor = new TaskExecutionLaunchDescriptor(
                    TaskExecutionLaunchDescriptor.CURRENT_VERSION,
                    launch.identity().engineId(),
                    launch.identity().executionId(),
                    launch.identity().runId(),
                    launch.identity().attempt(),
                    launch.deadlineAt(),
                    RunnerSparkMode.LOCAL,
                    new LaunchArtifactDownload(
                            access.manifestGetUrl(), launch.manifestSha256(), access.maximumManifestBytes()),
                    new LaunchArtifactUpload(access.resultPutUrl(), launch.resultKey()),
                    access.trialPreviewPutUrl() == null ? null : new LaunchArtifactUpload(
                            access.trialPreviewPutUrl(), trialPreviewKey(launch)),
                    launch.runnerEvent(),
                    launch.checkpointUriPrefix(),
                    launch.runnerControl(),
                    access.qualitySamples(),
                    access.userJar()
            );
            writeAtomic(launchFile, objectMapper.writeValueAsBytes(descriptor));
            writeAtomic(environmentFile, environment(launch, access).getBytes(StandardCharsets.UTF_8));
            return new LocalDockerWorkspace(realDirectory, launchFile, environmentFile);
        } catch (IOException | RuntimeException exception) {
            throw new BackendException("WORKSPACE_PREPARATION_FAILED", "无法准备 Local Docker 执行工作目录", exception);
        }
    }

    private static String trialPreviewKey(ExecutionLaunch launch) {
        return "task-runs/%s/attempts/%d/trial-preview.json".formatted(
                launch.identity().runId(), launch.identity().attempt());
    }

    public boolean readiness() {
        try {
            Path root = properties.absoluteWorkDirectory();
            Files.createDirectories(root);
            secureDirectory(root);
            Files.createDirectories(properties.absoluteCheckpointDirectory());
            secureDirectory(properties.absoluteCheckpointDirectory());
            Path probe = Files.createTempFile(root, ".dispatcher-ready-", ".tmp");
            try {
                Files.writeString(probe, "ready", StandardOpenOption.TRUNCATE_EXISTING);
            } finally {
                Files.deleteIfExists(probe);
            }
            return Files.isWritable(root);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private String environment(ExecutionLaunch launch, ArtifactLaunchAccess access) {
        List<String> lines = List.of(
                env("DATASCALPEL_TASK_LAUNCH_FILE", "/work/launch.json"),
                env("DATASCALPEL_TASK_WORK_DIRECTORY", "/work"),
                env("JAVA_TOOL_OPTIONS", runnerJavaOptions(launch))
        );
        return String.join("\n", lines) + "\n";
    }

    private static String env(String key, String value) {
        if (value == null || value.contains("\n") || value.contains("\r") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Runner 环境变量包含不支持的字符: " + key);
        }
        return key + "=" + value;
    }

    private String runnerJavaOptions(ExecutionLaunch launch) {
        int heapMiB = Math.max(256, launch.executionResources().driverMemoryMiB() * 3 / 4);
        String preserved = properties.runnerJavaOptions()
                .replaceAll("(?:^|\\s)-Xmx\\S+", "")
                .trim();
        String userOptions = DriverJavaOptions.extract(launch.sparkConf());
        return (preserved + " -Xmx" + heapMiB + "m"
                + (userOptions == null || userOptions.isBlank() ? "" : " " + userOptions)).trim();
    }

    private static void writeAtomic(Path target, byte[] content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(temporary)) {
            throw new IOException("临时文件不能是符号链接");
        }
        Files.write(temporary, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        secureFile(temporary);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        secureFile(target);
    }

    private static void secureDirectory(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Windows/non-POSIX filesystems rely on the process account ACL.
        }
    }

    private static void secureFile(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Windows/non-POSIX filesystems rely on the process account ACL.
        }
    }
}
