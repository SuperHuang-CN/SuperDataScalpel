package cn.superhuang.data.scalpel.dispatcher.backend.cluster;

import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactDownload;
import cn.superhuang.data.scalpel.contract.execution.LaunchArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.data.scalpel.dispatcher.artifact.ArtifactLaunchAccess;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Component
public class ClusterLaunchFileService {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final ObjectMapper objectMapper;

    public ClusterLaunchFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path create(Path configuredRoot, ExecutionLaunch launch, ArtifactLaunchAccess access)
            throws BackendException {
        try {
            Path root = configuredRoot.toAbsolutePath().normalize();
            Files.createDirectories(root);
            secureDirectory(root);
            Path realRoot = root.toRealPath();
            Path directory = realRoot.resolve(launch.identity().executionId().toString())
                    .resolve("attempt-" + launch.identity().attempt()).normalize();
            if (!directory.startsWith(realRoot)) throw new IOException("集群执行工作目录越界");
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
                throw new IOException("集群执行工作目录不能是符号链接");
            }
            Files.createDirectories(directory);
            secureDirectory(directory);
            Path file = directory.resolve("launch.json");
            TaskExecutionLaunchDescriptor descriptor = new TaskExecutionLaunchDescriptor(
                    TaskExecutionLaunchDescriptor.CURRENT_VERSION,
                    launch.identity().engineId(), launch.identity().executionId(), launch.identity().runId(),
                    launch.identity().attempt(), launch.deadlineAt(), RunnerSparkMode.CLUSTER,
                    new LaunchArtifactDownload(
                            access.manifestGetUrl(), launch.manifestSha256(), access.maximumManifestBytes()),
                    new LaunchArtifactUpload(access.resultPutUrl(), launch.resultKey()), launch.runnerEvent(),
                    launch.checkpointUriPrefix(), launch.runnerControl(), access.qualitySamples(), access.userJar());
            Files.write(file, objectMapper.writeValueAsBytes(descriptor), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            secureFile(file);
            return file;
        } catch (IOException | RuntimeException exception) {
            throw new BackendException("CLUSTER_LAUNCH_PREPARATION_FAILED", "无法准备集群 Runner 启动文件", exception);
        }
    }

    public boolean readiness(Path configuredRoot) {
        try {
            Path root = configuredRoot.toAbsolutePath().normalize();
            Files.createDirectories(root);
            secureDirectory(root);
            Path probe = Files.createTempFile(root, ".dispatcher-ready-", ".tmp");
            Files.deleteIfExists(probe);
            return Files.isWritable(root);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void secureDirectory(Path path) throws IOException {
        try { Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS); }
        catch (UnsupportedOperationException ignored) { }
    }

    private static void secureFile(Path path) throws IOException {
        try { Files.setPosixFilePermissions(path, FILE_PERMISSIONS); }
        catch (UnsupportedOperationException ignored) { }
    }
}
