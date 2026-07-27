package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

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
import cn.superhuang.data.scalpel.dispatcher.backend.cluster.ClusterLaunchFileService;
import cn.superhuang.data.scalpel.dispatcher.backend.command.CommandExecutor;
import cn.superhuang.data.scalpel.dispatcher.backend.command.CommandResult;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.KubernetesProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "data-scalpel.dispatcher", name = "backend", havingValue = "KUBERNETES")
public class KubernetesSparkExecutionBackend implements TaskExecutionBackend {
    private static final long CONTROL_BYTES = 2L * 1024 * 1024;

    private final KubernetesProperties properties;
    private final KubernetesCommandFactory commands;
    private final KubernetesPodParser parser;
    private final CommandExecutor executor;
    private final ClusterLaunchFileService launchFiles;
    private final DispatcherArtifactService artifacts;
    private final DispatcherProperties dispatcherProperties;

    public KubernetesSparkExecutionBackend(
            KubernetesProperties properties,
            CommandExecutor executor,
            ClusterLaunchFileService launchFiles,
            DispatcherArtifactService artifacts,
            DispatcherProperties dispatcherProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.commands = new KubernetesCommandFactory(properties);
        this.parser = new KubernetesPodParser(objectMapper);
        this.executor = executor;
        this.launchFiles = launchFiles;
        this.artifacts = artifacts;
        this.dispatcherProperties = dispatcherProperties;
    }

    @Override public ExecutionBackendType type() { return ExecutionBackendType.KUBERNETES; }

    @Override
    public BackendReadiness readiness() {
        List<String> issues = new ArrayList<>();
        if (!properties.immutableImage()) issues.add("Kubernetes Runner镜像必须使用不可变Digest");
        if (!launchFiles.readiness(properties.absoluteWorkDirectory())) issues.add("Kubernetes工作目录不可写");
        BackendReadiness artifactReadiness = artifacts.readiness();
        if (!artifactReadiness.ready()) issues.addAll(artifactReadiness.issues());
        try {
            CommandResult version = execute(commands.version(), properties.commandTimeout(), CONTROL_BYTES);
            if (!version.successful() || !version.outputText().contains("4.1.1")) {
                issues.add("spark-submit不可用或版本不是4.1.1");
            }
            if (!execute(commands.namespace(), properties.commandTimeout(), CONTROL_BYTES).successful()) {
                issues.add("Kubernetes Namespace不可访问");
            }
            for (String[] permission : requiredPermissions()) {
                CommandResult auth = execute(commands.authCanI(permission[0], permission[1]),
                        properties.commandTimeout(), CONTROL_BYTES);
                if (!auth.successful() || !"yes".equalsIgnoreCase(auth.outputText())) {
                    issues.add("Dispatcher缺少Kubernetes权限: " + permission[0] + " " + permission[1]);
                }
            }
        } catch (BackendException exception) {
            issues.add("Kubernetes Client不可用");
        }
        return issues.isEmpty() ? BackendReadiness.up() : new BackendReadiness(false, issues.stream().distinct().toList());
    }

    @Override
    public BackendSubmission submit(ExecutionLaunch launch) throws BackendException {
        requireLaunch(launch);
        ExecutionIdentity identity = launch.identity();
        Optional<KubernetesPodParser.ParsedPod> existing = driver(identity);
        if (existing.isPresent()) {
            parser.requireIdentity(existing.get(), identity);
            return submission(existing.get().name());
        }

        ArtifactLaunchAccess access = artifacts.prepareLaunch(launch);
        Path launchFile = launchFiles.create(properties.absoluteWorkDirectory(), launch, access);
        recreateSecret(identity, launchFile);
        try {
            CommandResult submitted = execute(commands.submit(identity), properties.submitTimeout(), CONTROL_BYTES);
            if (!submitted.successful()) {
                Optional<KubernetesPodParser.ParsedPod> raced = driver(identity);
                if (raced.isPresent()) {
                    parser.requireIdentity(raced.get(), identity);
                    return submission(raced.get().name());
                }
                throw new BackendException("KUBERNETES_SUBMIT_FAILED", "spark-submit提交Kubernetes任务失败");
            }
        } catch (BackendException exception) {
            Optional<KubernetesPodParser.ParsedPod> recovered = driver(identity);
            if (recovered.isPresent()) {
                parser.requireIdentity(recovered.get(), identity);
                return submission(recovered.get().name());
            }
            throw new BackendException("BACKEND_SUBMISSION_UNCERTAIN", "Kubernetes提交结果暂时无法确认", exception);
        }
        Optional<KubernetesPodParser.ParsedPod> created = driver(identity);
        if (created.isEmpty()) {
            throw new BackendException("BACKEND_SUBMISSION_UNCERTAIN", "spark-submit完成但Driver Pod尚未出现");
        }
        parser.requireIdentity(created.get(), identity);
        return submission(created.get().name());
    }

    @Override
    public BackendStatus inspect(ExternalExecutionHandle handle) throws BackendException {
        String podName = requireHandle(handle);
        CommandResult result = execute(commands.getDriver(podName), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful() && notFound(result)) {
            return new BackendStatus(BackendExecutionState.UNKNOWN, null, null, null, null);
        }
        if (!result.successful()) throw new BackendException("KUBERNETES_STATUS_FAILED", "无法读取Driver Pod状态");
        return parser.parse(result.outputText()).status();
    }

    @Override
    public BackendStatus inspect(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        String podName = requireHandle(handle);
        CommandResult result = execute(commands.getDriver(podName), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful() && notFound(result)) {
            return new BackendStatus(BackendExecutionState.UNKNOWN, null, null, null, null);
        }
        if (!result.successful()) throw new BackendException("KUBERNETES_STATUS_FAILED", "无法读取Driver Pod状态");
        KubernetesPodParser.ParsedPod pod = parser.parse(result.outputText());
        parser.requireIdentity(pod, identity);
        return pod.status();
    }

    @Override
    public void cancel(ExternalExecutionHandle handle) throws BackendException {
        throw new BackendException("EXECUTION_IDENTITY_REQUIRED", "Kubernetes取消必须提供完整执行身份");
    }

    @Override
    public void cancel(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        String podName = requireHandle(handle);
        Optional<KubernetesPodParser.ParsedPod> pod = driverByName(podName);
        if (pod.isPresent()) parser.requireIdentity(pod.get(), identity);
        requireSuccess(commands.deleteDriver(podName), "KUBERNETES_DELETE_FAILED", "无法删除Driver Pod");
        requireSuccess(commands.deleteExecutors(identity), "KUBERNETES_DELETE_FAILED", "无法删除Executor Pod");
    }

    @Override
    public BackendLog collectLog(ExternalExecutionHandle handle) throws BackendException {
        String podName = requireHandle(handle);
        CommandResult result = execute(commands.logs(podName), properties.commandTimeout(),
                dispatcherProperties.logMaxBytes().toBytes());
        if (!result.successful()) throw new BackendException("KUBERNETES_LOG_FAILED", "无法获取Driver Pod日志");
        return new BackendLog(result.output(), result.truncated());
    }

    @Override
    public Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) throws BackendException {
        CommandResult result = execute(commands.find(identity), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful()) throw new BackendException("KUBERNETES_RECOVERY_FAILED", "无法按Label恢复Driver Pod");
        List<KubernetesPodParser.ParsedPod> drivers = parser.parseList(result.outputText()).stream()
                .filter(pod -> pod.name().endsWith("-driver"))
                .toList();
        if (drivers.isEmpty()) return Optional.empty();
        if (drivers.size() != 1) throw new BackendException(
                "AMBIGUOUS_EXTERNAL_EXECUTION", "发现多个匹配身份的Kubernetes Driver Pod");
        parser.requireIdentity(drivers.getFirst(), identity);
        return Optional.of(new ExternalExecutionHandle(type(), drivers.getFirst().name(), null));
    }

    @Override
    public void cleanup(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        requireHandle(handle);
        requireSuccess(commands.deleteSecret(identity), "KUBERNETES_SECRET_DELETE_FAILED", "无法清理launch Secret");
    }

    @Override
    public void cleanup(ExecutionIdentity identity) throws BackendException {
        requireSuccess(commands.deleteSecret(identity), "KUBERNETES_SECRET_DELETE_FAILED", "无法清理遗留launch Secret");
    }

    private static List<String[]> requiredPermissions() {
        return List.of(
                new String[]{"get", "pods"}, new String[]{"list", "pods"},
                new String[]{"create", "pods"}, new String[]{"delete", "pods"},
                new String[]{"get", "pods/log"},
                new String[]{"create", "secrets"}, new String[]{"get", "secrets"},
                new String[]{"delete", "secrets"},
                new String[]{"create", "services"}, new String[]{"get", "services"},
                new String[]{"delete", "services"},
                new String[]{"create", "configmaps"}, new String[]{"get", "configmaps"},
                new String[]{"delete", "configmaps"}
        );
    }

    private void recreateSecret(ExecutionIdentity identity, Path launchFile) throws BackendException {
        CommandResult existing = execute(commands.getSecret(identity), properties.commandTimeout(), CONTROL_BYTES);
        if (existing.successful()) {
            if (!existing.outputText().equals("secret/" + KubernetesNames.secret(identity))) {
                throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "同名Kubernetes Secret身份不匹配");
            }
            requireSuccess(commands.deleteSecret(identity), "KUBERNETES_SECRET_DELETE_FAILED", "无法替换launch Secret");
        } else if (!notFound(existing)) {
            throw new BackendException("KUBERNETES_SECRET_READ_FAILED", "无法检查launch Secret");
        }
        requireSuccess(commands.createSecret(identity, launchFile), "KUBERNETES_SECRET_CREATE_FAILED", "无法创建launch Secret");
        requireSuccess(commands.labelSecret(identity), "KUBERNETES_SECRET_LABEL_FAILED", "无法标记launch Secret");
    }

    private Optional<KubernetesPodParser.ParsedPod> driver(ExecutionIdentity identity) throws BackendException {
        return driverByName(KubernetesNames.driverPod(identity));
    }

    private Optional<KubernetesPodParser.ParsedPod> driverByName(String podName) throws BackendException {
        CommandResult result = execute(commands.getDriver(podName), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful() && notFound(result)) return Optional.empty();
        if (!result.successful()) throw new BackendException("KUBERNETES_STATUS_FAILED", "无法读取Driver Pod");
        return Optional.of(parser.parse(result.outputText()));
    }

    private void requireSuccess(List<String> command, String code, String message) throws BackendException {
        if (!execute(command, properties.commandTimeout(), CONTROL_BYTES).successful()) {
            throw new BackendException(code, message);
        }
    }

    private CommandResult execute(List<String> command, java.time.Duration timeout, long maximumBytes)
            throws BackendException {
        return executor.execute(command, timeout, maximumBytes);
    }

    private BackendSubmission submission(String podName) {
        return new BackendSubmission(new ExternalExecutionHandle(type(), podName, null));
    }

    private static boolean notFound(CommandResult result) {
        String text = result.outputText().toLowerCase();
        return text.contains("notfound") || text.contains("not found");
    }

    private static void requireLaunch(ExecutionLaunch launch) throws BackendException {
        if (launch == null || launch.identity() == null || launch.manifestSha256() == null
                || !launch.manifestSha256().matches("[0-9a-fA-F]{64}")) {
            throw new BackendException("INVALID_EXECUTION_LAUNCH", "Kubernetes执行启动参数无效");
        }
    }

    private static String requireHandle(ExternalExecutionHandle handle) throws BackendException {
        if (handle == null || handle.backendType() != ExecutionBackendType.KUBERNETES
                || handle.externalId() == null
                || !handle.externalId().matches("ds-[0-9a-f]{32}-driver")) {
            throw new BackendException("INVALID_EXTERNAL_EXECUTION_HANDLE", "Kubernetes执行Handle无效");
        }
        return handle.externalId();
    }
}
