package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.dispatcher.artifact.ArtifactLaunchAccess;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendLog;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendStatus;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendSubmission;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.backend.TaskExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "data-scalpel.dispatcher", name = "backend", havingValue = "LOCAL_DOCKER", matchIfMissing = true)
public class LocalDockerExecutionBackend implements TaskExecutionBackend {
    private static final Logger log = LoggerFactory.getLogger(LocalDockerExecutionBackend.class);
    private static final long CONTROL_OUTPUT_BYTES = 2L * 1024 * 1024;
    private static final Pattern SENSITIVE_DIAGNOSTIC = Pattern.compile(
            "(?i)(password|secret|token|signature|credential)([=:])([^\\s,;]+)");

    private final DockerCli dockerCli;
    private final DockerCommandFactory commands;
    private final DockerInspectParser parser;
    private final LocalDockerWorkspaceService workspaceService;
    private final DispatcherArtifactService artifactService;
    private final LocalDockerProperties properties;
    private final DispatcherProperties dispatcherProperties;

    public LocalDockerExecutionBackend(
            DockerCli dockerCli,
            DockerCommandFactory commands,
            DockerInspectParser parser,
            LocalDockerWorkspaceService workspaceService,
            DispatcherArtifactService artifactService,
            LocalDockerProperties properties,
            DispatcherProperties dispatcherProperties
    ) {
        this.dockerCli = dockerCli;
        this.commands = commands;
        this.parser = parser;
        this.workspaceService = workspaceService;
        this.artifactService = artifactService;
        this.properties = properties;
        this.dispatcherProperties = dispatcherProperties;
    }

    @Override
    public ExecutionBackendType type() { return ExecutionBackendType.LOCAL_DOCKER; }

    @Override
    public BackendReadiness readiness() {
        List<String> issues = new ArrayList<>();
        if (!Files.isRegularFile(properties.absoluteRunnerJar()) || !Files.isReadable(properties.absoluteRunnerJar())) {
            issues.add("Task Runner JAR 不存在或不可读");
        }
        if (!workspaceService.readiness()) issues.add("Local Docker 工作目录不可写");
        BackendReadiness artifacts = artifactService.readiness();
        if (!artifacts.ready()) issues.addAll(artifacts.issues());
        try {
            DockerCommandResult version = execute(commands.version(), properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
            if (!version.successful()) issues.add("Docker Server 不可访问");
            if (version.successful()) inspectPlatforms(issues);
        } catch (BackendException exception) {
            issues.add("Docker Server 不可访问");
        }
        return issues.isEmpty() ? BackendReadiness.up() : new BackendReadiness(false, issues.stream().distinct().toList());
    }

    @Override
    public BackendSubmission submit(ExecutionLaunch launch) throws BackendException {
        validateLaunch(launch);
        ArtifactLaunchAccess access = artifactService.prepareLaunch(launch);
        LocalDockerWorkspace workspace = workspaceService.create(launch, access);
        List<String> existing = find(commands.findByName(launch.identity()));
        if (existing.size() > 1) throw ambiguous();
        if (existing.size() == 1) {
            DockerContainerInspection inspection = inspectExisting(existing.getFirst());
            requireIdentity(inspection, launch.identity());
            startCreatedBestEffort(inspection);
            return submission(inspection.id());
        }

        DockerCommandResult created;
        try {
            created = execute(commands.create(launch.identity(), workspace.directory(), workspace.environmentFile()),
                    properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        } catch (IllegalArgumentException exception) {
            throw new BackendException("INVALID_DOCKER_CONFIGURATION", "Local Docker 配置包含不支持的值", exception);
        }
        if (!created.successful()) {
            Optional<DockerContainerInspection> raced = findSingleByName(launch.identity());
            if (raced.isPresent()) {
                DockerContainerInspection inspection = raced.get();
                startCreatedBestEffort(inspection);
                return submission(inspection.id());
            }
            throw new BackendException("DOCKER_CREATE_FAILED", withDiagnostic("Docker 容器创建失败", created));
        }
        logSuccessfulWarning("docker create", created);
        DockerContainerInspection inspection = findSingleByName(launch.identity())
                .orElseThrow(() -> new BackendException(
                        "DOCKER_CONTAINER_RECOVERY_FAILED",
                        "docker create 成功，但无法定位对应容器"));
        startCreatedBestEffort(inspection);
        return submission(inspection.id());
    }

    @Override
    public BackendStatus inspect(ExternalExecutionHandle handle) throws BackendException {
        requireHandle(handle);
        Optional<DockerContainerInspection> inspection = inspectIfPresent(handle.externalId());
        if (inspection.isEmpty()) return status(BackendExecutionState.UNKNOWN, null, null, null, null);
        if ("created".equalsIgnoreCase(inspection.get().status())) startCreatedBestEffort(inspection.get());
        DockerContainerInspection current = "created".equalsIgnoreCase(inspection.get().status())
                ? inspectIfPresent(handle.externalId()).orElse(inspection.get()) : inspection.get();
        return statusOf(current);
    }

    @Override
    public void cancel(ExternalExecutionHandle handle) throws BackendException {
        cancelExisting(handle, null);
    }

    @Override
    public void cancel(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        requireHandle(handle);
        if (identity == null) throw new BackendException("INVALID_EXECUTION_IDENTITY", "执行身份不能为空");
        Optional<DockerContainerInspection> direct = inspectIfPresent(handle.externalId());
        if (direct.isPresent()) {
            requireIdentity(direct.get(), identity);
            cancelInspection(direct.get());
            return;
        }
        Optional<ExternalExecutionHandle> recovered = recover(identity);
        if (recovered.isEmpty()) return;
        DockerContainerInspection inspection = inspectIfPresent(recovered.get().externalId())
                .orElseThrow(() -> new BackendException("EXTERNAL_EXECUTION_NOT_FOUND", "恢复后未找到待取消的 Docker 容器"));
        requireIdentity(inspection, identity);
        cancelInspection(inspection);
    }

    private void cancelExisting(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        requireHandle(handle);
        DockerContainerInspection inspection = inspectIfPresent(handle.externalId())
                .orElseThrow(() -> new BackendException("EXTERNAL_EXECUTION_NOT_FOUND", "没有找到待取消的 Docker 容器"));
        if (identity != null) requireIdentity(inspection, identity);
        cancelInspection(inspection);
    }

    private void cancelInspection(DockerContainerInspection inspection) throws BackendException {
        String state = inspection.status().toLowerCase(Locale.ROOT);
        if ("created".equals(state)) {
            requireSuccess(commands.remove(inspection.id()), properties.commandTimeout(), "DOCKER_REMOVE_FAILED", "无法删除未启动容器");
            return;
        }
        if (!active(state, inspection.running())) return;
        DockerCommandResult stopped = execute(commands.stop(inspection.id()),
                properties.stopTimeout().plusSeconds(5), CONTROL_OUTPUT_BYTES);
        Optional<DockerContainerInspection> afterStop = inspectIfPresent(inspection.id());
        if (!stopped.successful() || afterStop.filter(value -> active(value.status(), value.running())).isPresent()) {
            requireSuccess(commands.kill(inspection.id()), Duration.ofSeconds(10),
                    "DOCKER_KILL_FAILED", "无法强制停止 Docker 容器");
        }
    }

    @Override
    public BackendLog collectLog(ExternalExecutionHandle handle) throws BackendException {
        requireHandle(handle);
        if (inspectIfPresent(handle.externalId()).isEmpty()) {
            throw new BackendException("EXTERNAL_EXECUTION_NOT_FOUND", "没有找到 Docker 容器日志");
        }
        DockerCommandResult logs = execute(commands.logs(handle.externalId()), properties.commandTimeout(),
                dispatcherProperties.logMaxBytes().toBytes());
        if (!logs.successful()) throw new BackendException("DOCKER_LOG_FAILED", "无法读取 Docker 容器日志");
        return new BackendLog(logs.combinedOutput(), logs.truncated());
    }

    @Override
    public Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) throws BackendException {
        if (identity == null) throw new BackendException("INVALID_EXECUTION_IDENTITY", "执行身份不能为空");
        List<String> matches = find(commands.findByIdentity(identity));
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() > 1) throw ambiguous();
        DockerContainerInspection inspection = inspectExisting(matches.getFirst());
        requireIdentity(inspection, identity);
        startCreatedBestEffort(inspection);
        return Optional.of(new ExternalExecutionHandle(type(), inspection.id(), null));
    }

    @Override
    public void cleanup(ExternalExecutionHandle handle) throws BackendException {
        requireHandle(handle);
        Optional<DockerContainerInspection> inspection = inspectIfPresent(handle.externalId());
        if (inspection.isEmpty()) return;
        if (active(inspection.get().status(), inspection.get().running())
                || "created".equalsIgnoreCase(inspection.get().status())) {
            throw new BackendException("EXTERNAL_EXECUTION_ACTIVE", "仍在运行的 Docker 容器不能清理");
        }
        requireSuccess(commands.remove(inspection.get().id()), properties.commandTimeout(),
                "DOCKER_REMOVE_FAILED", "无法清理 Docker 容器");
    }

    private Optional<DockerContainerInspection> inspectIfPresent(String reference) throws BackendException {
        List<String> matches = find(commands.findById(reference));
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() > 1) throw ambiguous();
        return Optional.of(inspectExisting(matches.getFirst()));
    }

    private DockerContainerInspection inspectExisting(String reference) throws BackendException {
        DockerCommandResult result = execute(commands.inspect(reference), properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        if (!result.successful()) {
            throw new BackendException("DOCKER_INSPECT_FAILED", withDiagnostic("无法读取 Docker 容器状态", result));
        }
        logSuccessfulWarning("docker inspect", result);
        return parser.parseInspection(result.stdoutText());
    }

    private List<String> find(List<String> command) throws BackendException {
        DockerCommandResult result = execute(command, properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        if (!result.successful()) {
            throw new BackendException("DOCKER_DISCOVERY_FAILED", withDiagnostic("无法查询 Docker 容器", result));
        }
        logSuccessfulWarning("docker ps", result);
        return parser.parseContainerIds(result.stdoutText());
    }

    private Optional<DockerContainerInspection> findSingleByName(ExecutionIdentity identity) throws BackendException {
        List<String> matches = find(commands.findByName(identity));
        if (matches.size() > 1) throw ambiguous();
        if (matches.isEmpty()) return Optional.empty();
        DockerContainerInspection inspection = inspectExisting(matches.getFirst());
        requireIdentity(inspection, identity);
        return Optional.of(inspection);
    }

    private void startCreatedBestEffort(DockerContainerInspection inspection) throws BackendException {
        if (!"created".equalsIgnoreCase(inspection.status())) return;
        DockerCommandResult result = execute(commands.start(inspection.id()), properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        if (!result.successful()) {
            log.warn("Docker container {} remains created and will be retried: {}",
                    inspection.id(), safeDiagnostic(result));
        } else {
            logSuccessfulWarning("docker start", result);
        }
    }

    private void requireSuccess(
            List<String> command,
            Duration timeout,
            String code,
            String message
    ) throws BackendException {
        if (!execute(command, timeout, CONTROL_OUTPUT_BYTES).successful()) throw new BackendException(code, message);
    }

    private DockerCommandResult execute(List<String> command, Duration timeout, long maxBytes) throws BackendException {
        return dockerCli.execute(command, timeout, maxBytes);
    }

    private void inspectPlatforms(List<String> issues) throws BackendException {
        DockerCommandResult serverResult = execute(
                commands.serverPlatform(), properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        String serverPlatform = serverResult.successful() ? normalizedPlatform(serverResult.stdoutText()) : null;
        if (serverPlatform == null) {
            issues.add("无法识别 Docker Server 平台");
        }

        String configuredPlatform = normalizedPlatform(properties.platform());
        if (configuredPlatform != null && serverPlatform != null && !configuredPlatform.equals(serverPlatform)) {
            issues.add("配置平台 " + configuredPlatform + " 与 Docker Server " + serverPlatform + " 不一致");
        }
        String expectedPlatform = configuredPlatform != null ? configuredPlatform : serverPlatform;

        DockerCommandResult imageResult = execute(
                commands.imagePlatformInspect(), properties.commandTimeout(), CONTROL_OUTPUT_BYTES);
        if (!imageResult.successful()) {
            if ("never".equals(properties.pull())) issues.add("配置的 Docker 镜像不存在且 pull=never");
            return;
        }
        String imagePlatform = normalizedPlatform(imageResult.stdoutText());
        if (imagePlatform == null) {
            issues.add("无法识别 Runner Docker 镜像平台");
        } else if (expectedPlatform != null && !expectedPlatform.equals(imagePlatform)
                && !"always".equals(properties.pull())) {
            issues.add("Runner 镜像平台 " + imagePlatform + " 与期望平台 " + expectedPlatform + " 不一致");
        }
    }

    private static String normalizedPlatform(String value) {
        if (value == null || value.isBlank()) return null;
        String[] components = value.trim().toLowerCase(Locale.ROOT).replace("\"", "").split("/");
        if (components.length < 2 || components[0].isBlank() || components[1].isBlank()) return null;
        String architecture = switch (components[1]) {
            case "aarch64" -> "arm64";
            case "x86_64" -> "amd64";
            default -> components[1];
        };
        return components[0] + "/" + architecture;
    }

    private static String withDiagnostic(String message, DockerCommandResult result) {
        String diagnostic = safeDiagnostic(result);
        return diagnostic.isBlank() ? message : message + "：" + diagnostic;
    }

    private static String safeDiagnostic(DockerCommandResult result) {
        String diagnostic = result.stderrText();
        if (diagnostic.isBlank()) diagnostic = result.stdoutText();
        if (diagnostic.isBlank()) return "";
        String normalized = diagnostic.replace('\n', ' ').replace('\r', ' ').trim();
        normalized = SENSITIVE_DIAGNOSTIC.matcher(normalized).replaceAll("$1$2***");
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    private static void logSuccessfulWarning(String operation, DockerCommandResult result) {
        if (!result.stderrText().isBlank()) {
            log.warn("{} completed with Docker warning: {}", operation, safeDiagnostic(result));
        }
    }

    private BackendStatus statusOf(DockerContainerInspection inspection) {
        String state = inspection.status().toLowerCase(Locale.ROOT);
        return switch (state) {
            case "created" -> status(BackendExecutionState.PENDING, null, null, null, null);
            case "running", "restarting", "paused" -> status(
                    BackendExecutionState.RUNNING, inspection.parsedStartedAt(), null, null, null);
            case "exited" -> inspection.exitCode() == 0
                    ? status(BackendExecutionState.SUCCEEDED, inspection.parsedStartedAt(), inspection.parsedFinishedAt(), null, null)
                    : status(BackendExecutionState.FAILED, inspection.parsedStartedAt(), inspection.parsedFinishedAt(),
                    "CONTAINER_EXIT_NON_ZERO", "Runner 容器以非零状态退出（" + inspection.exitCode() + "）");
            case "dead" -> status(BackendExecutionState.FAILED, inspection.parsedStartedAt(), inspection.parsedFinishedAt(),
                    "CONTAINER_DEAD", "Runner 容器异常终止");
            default -> status(BackendExecutionState.UNKNOWN, inspection.parsedStartedAt(), inspection.parsedFinishedAt(), null, null);
        };
    }

    private static BackendStatus status(
            BackendExecutionState state,
            java.time.Instant startedAt,
            java.time.Instant endedAt,
            String code,
            String message
    ) {
        return new BackendStatus(state, startedAt, endedAt, code, message);
    }

    private static boolean active(String state, boolean running) {
        return running || "running".equalsIgnoreCase(state) || "restarting".equalsIgnoreCase(state)
                || "paused".equalsIgnoreCase(state);
    }

    private static void validateLaunch(ExecutionLaunch launch) throws BackendException {
        if (launch == null || launch.identity() == null) {
            throw new BackendException("INVALID_EXECUTION_LAUNCH", "执行启动参数不能为空");
        }
        if (!launch.manifestSha256().matches("[0-9a-fA-F]{64}")) {
            throw new BackendException("INVALID_MANIFEST_DIGEST", "manifest SHA-256 格式无效");
        }
    }

    private static void requireHandle(ExternalExecutionHandle handle) throws BackendException {
        if (handle == null || handle.backendType() != ExecutionBackendType.LOCAL_DOCKER
                || !handle.externalId().matches("[0-9a-fA-F]{64}")) {
            throw new BackendException("INVALID_EXTERNAL_EXECUTION_HANDLE", "Local Docker 执行 Handle 无效");
        }
    }

    private static void requireIdentity(DockerContainerInspection inspection, ExecutionIdentity identity)
            throws BackendException {
        if (!inspection.hasIdentity(identity)) {
            throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "同名 Docker 容器的执行身份不匹配");
        }
    }

    private BackendSubmission submission(String id) {
        return new BackendSubmission(new ExternalExecutionHandle(type(), id, null));
    }

    private static BackendException ambiguous() {
        return new BackendException("AMBIGUOUS_EXTERNAL_EXECUTION", "发现多个匹配的 Docker 容器，需要人工处理");
    }
}
