package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DockerCommandFactory {
    static final String MANAGED_LABEL = "cn.superhuang.datascalpel.managed";
    static final String ENGINE_ID_LABEL = "cn.superhuang.datascalpel.engine-id";
    static final String EXECUTION_ID_LABEL = "cn.superhuang.datascalpel.execution-id";
    static final String RUN_ID_LABEL = "cn.superhuang.datascalpel.run-id";
    static final String ATTEMPT_LABEL = "cn.superhuang.datascalpel.attempt";
    static final String INSPECT_FORMAT = "{\"id\":{{json .Id}},\"name\":{{json .Name}},"
            + "\"createdAt\":{{json .Created}},\"labels\":{{json .Config.Labels}},"
            + "\"status\":{{json .State.Status}},\"running\":{{json .State.Running}},"
            + "\"startedAt\":{{json .State.StartedAt}},\"finishedAt\":{{json .State.FinishedAt}},"
            + "\"exitCode\":{{json .State.ExitCode}},\"error\":{{json .State.Error}}}";

    private final LocalDockerProperties properties;

    public DockerCommandFactory(LocalDockerProperties properties) {
        this.properties = properties;
    }

    public List<String> version() {
        return command("version", "--format", "{{json .Server.Version}}");
    }

    public List<String> serverPlatform() {
        return command("info", "--format", "{{.OSType}}/{{.Architecture}}");
    }

    public List<String> imagePlatformInspect() {
        return command("image", "inspect", "--format", "{{.Os}}/{{.Architecture}}", properties.image());
    }

    public List<String> create(
            ExecutionIdentity identity,
            Path executionWorkDirectory,
            Path environmentFile
    ) {
        Path runnerJar = safeMountPath(properties.absoluteRunnerJar());
        Path workDirectory = safeMountPath(executionWorkDirectory);
        Path checkpointDirectory = safeMountPath(properties.absoluteCheckpointDirectory());
        List<String> result = new ArrayList<>();
        result.add(properties.executable());
        result.addAll(List.of(
                "create",
                "--name", containerName(identity)
        ));
        if (properties.platform() != null) {
            result.addAll(List.of("--platform", properties.platform()));
        }
        result.addAll(List.of(
                "--pull", properties.pull(),
                "--memory", properties.memory(),
                "--cpus", properties.cpus(),
                "--add-host", "host.docker.internal:host-gateway",
                "--label", MANAGED_LABEL + "=true",
                "--label", ENGINE_ID_LABEL + "=" + identity.engineId(),
                "--label", EXECUTION_ID_LABEL + "=" + identity.executionId(),
                "--label", RUN_ID_LABEL + "=" + identity.runId(),
                "--label", ATTEMPT_LABEL + "=" + identity.attempt(),
                "--mount", "type=bind,source=" + runnerJar + ",target=/opt/datascalpel/task-runner.jar,readonly",
                "--mount", "type=bind,source=" + workDirectory + ",target=/work",
                "--mount", "type=bind,source=" + checkpointDirectory + ",target=/checkpoints",
                "--env-file", environmentFile.toAbsolutePath().normalize().toString(),
                properties.image(),
                "java", "-jar", "/opt/datascalpel/task-runner.jar"
        ));
        return List.copyOf(result);
    }

    public List<String> start(String reference) { return command("start", reference(reference)); }

    public List<String> inspect(String reference) {
        return command("inspect", "--format", INSPECT_FORMAT, reference(reference));
    }

    public List<String> findByName(ExecutionIdentity identity) {
        return command("ps", "-a", "--no-trunc", "--filter", "name=^/" + containerName(identity) + "$",
                "--format", "{{json .ID}}");
    }

    public List<String> findByIdentity(ExecutionIdentity identity) {
        return command("ps", "-a", "--no-trunc",
                "--filter", "label=" + MANAGED_LABEL + "=true",
                "--filter", "label=" + ENGINE_ID_LABEL + "=" + identity.engineId(),
                "--filter", "label=" + EXECUTION_ID_LABEL + "=" + identity.executionId(),
                "--format", "{{json .ID}}");
    }

    public List<String> findById(String reference) {
        return command("ps", "-a", "--no-trunc", "--filter", "id=" + reference(reference),
                "--format", "{{json .ID}}");
    }

    public List<String> logs(String reference) {
        return command("logs", "--timestamps", reference(reference));
    }

    public List<String> stop(String reference) {
        return command("stop", "--time", Long.toString(properties.stopTimeout().toSeconds()), reference(reference));
    }

    public List<String> kill(String reference) { return command("kill", reference(reference)); }

    public List<String> remove(String reference) { return command("rm", reference(reference)); }

    public static String containerName(ExecutionIdentity identity) {
        if (identity == null) throw new IllegalArgumentException("执行身份不能为空");
        return "datascalpel-runner-" + identity.executionId();
    }

    private List<String> command(String... arguments) {
        List<String> result = new ArrayList<>(arguments.length + 1);
        result.add(properties.executable());
        result.addAll(List.of(arguments));
        return List.copyOf(result);
    }

    private static Path safeMountPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.toString().contains(",") || normalized.toString().contains("\n")
                || normalized.toString().contains("\r")) {
            throw new IllegalArgumentException("Docker mount 路径包含不支持的字符");
        }
        return normalized;
    }

    private static String reference(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,299}")) {
            throw new IllegalArgumentException("Docker 容器引用无效");
        }
        return value;
    }
}
