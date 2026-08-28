package cn.superhuang.data.scalpel.dispatcher.backend.yarn;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.dispatcher.artifact.ArtifactLaunchAccess;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
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
import cn.superhuang.data.scalpel.dispatcher.config.YarnProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "data-scalpel.dispatcher", name = "backend", havingValue = "YARN")
public class YarnSparkExecutionBackend implements TaskExecutionBackend {
    private static final long CONTROL_BYTES = 2L * 1024 * 1024;

    private final YarnProperties properties;
    private final YarnCommandFactory commands;
    private final YarnStatusParser parser;
    private final CommandExecutor executor;
    private final ClusterLaunchFileService launchFiles;
    private final DispatcherArtifactService artifacts;
    private final DispatcherProperties dispatcherProperties;

    public YarnSparkExecutionBackend(
            YarnProperties properties,
            CommandExecutor executor,
            ClusterLaunchFileService launchFiles,
            DispatcherArtifactService artifacts,
            DispatcherProperties dispatcherProperties
    ) {
        this.properties = properties;
        this.commands = new YarnCommandFactory(properties);
        this.parser = new YarnStatusParser();
        this.executor = executor;
        this.launchFiles = launchFiles;
        this.artifacts = artifacts;
        this.dispatcherProperties = dispatcherProperties;
    }

    @Override public ExecutionBackendType type() { return ExecutionBackendType.YARN; }

    @Override
    public BackendReadiness readiness() {
        List<String> issues = new ArrayList<>();
        if (!launchFiles.readiness(properties.absoluteWorkDirectory())) issues.add("YARN工作目录不可写");
        BackendReadiness artifactReadiness = artifacts.readiness();
        if (!artifactReadiness.ready()) issues.addAll(artifactReadiness.issues());
        try {
            CommandResult version = execute(commands.version(), properties.commandTimeout(), CONTROL_BYTES);
            if (!version.successful() || !version.outputText().contains("4.1.1")) {
                issues.add("spark-submit不可用或版本不是4.1.1");
            }
            if (!execute(commands.nodes(), properties.commandTimeout(), CONTROL_BYTES).successful()) {
                issues.add("YARN ResourceManager不可访问");
            }
            if (!execute(commands.runnerJarReadable(), properties.commandTimeout(), CONTROL_BYTES).successful()) {
                issues.add("HDFS上的Cluster Runner JAR不可读");
            }
            CommandResult javaVersion = execute(commands.javaVersion(), properties.commandTimeout(), CONTROL_BYTES);
            if (!javaVersion.successful() || !javaVersion.outputText().matches("(?s).*version[ \\\"]+21(?:[._\\\" ].*)?")) {
                issues.add("Dispatcher提交环境必须使用Java 21");
            }
        } catch (BackendException exception) {
            issues.add("YARN Client不可用");
        }
        return issues.isEmpty() ? BackendReadiness.up() : new BackendReadiness(false, issues.stream().distinct().toList());
    }

    @Override
    public BackendSubmission submit(ExecutionLaunch launch) throws BackendException {
        requireLaunch(launch);
        ArtifactLaunchAccess access = artifacts.prepareLaunch(launch);
        Path launchFile = launchFiles.create(properties.absoluteWorkDirectory(), launch, access);
        try {
            CommandResult result = execute(
                    commands.submit(launch.identity(), launchFile, launch.sparkConf(), launch.executionResources()), properties.submitTimeout(), CONTROL_BYTES);
            if (result.successful()) {
                try {
                    String applicationId = parser.uniqueApplicationId(result.outputText());
                    return submission(applicationId);
                } catch (BackendException missingId) {
                    if (!"YARN_APPLICATION_ID_MISSING".equals(missingId.code())) throw missingId;
                }
            }
        } catch (BackendException exception) {
            if ("YARN_APPLICATION_ID_AMBIGUOUS".equals(exception.code())) throw exception;
        }
        Optional<ExternalExecutionHandle> recovered = recover(launch.identity());
        if (recovered.isPresent()) return new BackendSubmission(recovered.get());
        throw new BackendException("BACKEND_SUBMISSION_UNCERTAIN", "YARN提交结果暂时无法确认");
    }

    @Override
    public BackendStatus inspect(ExternalExecutionHandle handle) throws BackendException {
        String applicationId = requireHandle(handle);
        CommandResult result = execute(commands.status(applicationId), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful()) {
            if (result.outputText().toLowerCase().contains("doesn't exist")) {
                return new BackendStatus(cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState.UNKNOWN,
                        null, null, null, null);
            }
            throw new BackendException("YARN_STATUS_FAILED", "无法读取YARN Application状态");
        }
        return parser.parseStatus(result.outputText()).status();
    }

    @Override
    public void cancel(ExternalExecutionHandle handle) throws BackendException {
        String applicationId = requireHandle(handle);
        CommandResult result = execute(commands.kill(applicationId), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful() && !result.outputText().toLowerCase().contains("finished")) {
            throw new BackendException("YARN_KILL_FAILED", "无法停止YARN Application");
        }
    }

    @Override
    public void cancel(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        String applicationId = requireHandle(handle);
        Optional<ExternalExecutionHandle> recovered = recover(identity);
        if (recovered.isPresent() && !applicationId.equals(recovered.get().externalId())) {
            throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "YARN Application与执行身份不匹配");
        }
        if (recovered.isEmpty()) {
            BackendStatus status = inspect(handle);
            if (status.state() == cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState.SUCCEEDED
                    || status.state() == cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState.FAILED
                    || status.state() == cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState.CANCELLED
                    || status.state() == cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState.UNKNOWN) return;
            throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "无法确认YARN Application的执行身份");
        }
        cancel(handle);
    }

    @Override
    public BackendLog collectLog(ExternalExecutionHandle handle) throws BackendException {
        String applicationId = requireHandle(handle);
        CommandResult result = execute(commands.logs(applicationId), properties.commandTimeout(),
                dispatcherProperties.logMaxBytes().toBytes());
        if (!result.successful()) throw new BackendException("YARN_LOG_FAILED", "无法获取YARN聚合日志");
        return new BackendLog(result.output(), result.truncated());
    }

    @Override
    public Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) throws BackendException {
        if (identity == null) throw new BackendException("INVALID_EXECUTION_IDENTITY", "执行身份不能为空");
        CommandResult result = execute(commands.recover(identity), properties.commandTimeout(), CONTROL_BYTES);
        if (!result.successful()) throw new BackendException("YARN_RECOVERY_FAILED", "无法按Tag查询YARN Application");
        Set<String> ids = parser.applicationIds(result.outputText());
        if (ids.isEmpty()) return Optional.empty();
        if (ids.size() != 1) throw new BackendException(
                "AMBIGUOUS_EXTERNAL_EXECUTION", "发现多个匹配执行Tag的YARN Application");
        return Optional.of(new ExternalExecutionHandle(type(), ids.iterator().next(), null));
    }

    private BackendSubmission submission(String applicationId) {
        return new BackendSubmission(new ExternalExecutionHandle(type(), applicationId, null));
    }

    private CommandResult execute(List<String> command, java.time.Duration timeout, long maximumBytes)
            throws BackendException {
        return executor.execute(command, timeout, maximumBytes);
    }

    private static void requireLaunch(ExecutionLaunch launch) throws BackendException {
        if (launch == null || launch.identity() == null || launch.manifestSha256() == null
                || !launch.manifestSha256().matches("[0-9a-fA-F]{64}")) {
            throw new BackendException("INVALID_EXECUTION_LAUNCH", "YARN执行启动参数无效");
        }
    }

    private static String requireHandle(ExternalExecutionHandle handle) throws BackendException {
        if (handle == null || handle.backendType() != ExecutionBackendType.YARN
                || !YarnStatusParser.APPLICATION_ID.matcher(handle.externalId()).matches()) {
            throw new BackendException("INVALID_EXTERNAL_EXECUTION_HANDLE", "YARN执行Handle无效");
        }
        return handle.externalId();
    }
}
