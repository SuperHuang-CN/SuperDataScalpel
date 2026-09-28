package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.backend.cluster.ClusterLaunchFileService;
import cn.superhuang.data.scalpel.dispatcher.backend.command.ProcessBuilderCommandExecutor;
import cn.superhuang.data.scalpel.dispatcher.backend.kubernetes.KubernetesSparkExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.backend.localdocker.*;
import cn.superhuang.data.scalpel.dispatcher.backend.yarn.YarnSparkExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.config.*;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Explicit, deployment-time construction; no mutable current target or process-wide context switching. */
@Component
public class DispatcherBackendRegistry {
    public record Target(String key, String name, String fingerprint, TaskExecutionBackend backend,
                         DispatcherTargetsProperties.Target configuration) { }

    private final Map<String, Target> targets;
    private final boolean legacy;
    private final TargetReadinessCache readiness;

    public DispatcherBackendRegistry(DispatcherTargetsProperties configured, TaskExecutionBackend legacyBackend,
            LocalDockerProperties docker, YarnProperties yarn, KubernetesProperties kubernetes,
            DispatcherStreamingProperties streaming, DispatcherProperties dispatcher,
            DispatcherArtifactService artifacts, ObjectMapper mapper, org.springframework.core.env.Environment environment) {
        legacy = environment.getProperty("data-scalpel.dispatcher.legacy-single-backend", Boolean.class, false);
        Map<String, Target> values = new LinkedHashMap<>();
        if (legacy) {
            var config = new DispatcherTargetsProperties.Target(true, "默认执行目标", legacyBackend.type(),
                    docker, yarn, kubernetes, streaming);
            values.put("default", new Target("default", "默认执行目标", fingerprint(config), legacyBackend, config));
        } else {
            configured.targets().forEach((key, raw) -> {
                if (!raw.enabled()) return;
                var config = DispatcherTargetConfiguration.resolve(environment, key, raw);
                var executor = new ProcessBuilderCommandExecutor();
                var files = new ClusterLaunchFileService(mapper);
                TaskExecutionBackend backend = switch (config.backend()) {
                    case LOCAL_DOCKER -> new LocalDockerExecutionBackend(new ProcessBuilderDockerCli(),
                            new DockerCommandFactory(config.localDocker()), new DockerInspectParser(mapper),
                            new LocalDockerWorkspaceService(config.localDocker(), mapper), artifacts,
                            config.localDocker(), dispatcher);
                    case KUBERNETES -> new KubernetesSparkExecutionBackend(config.kubernetes(), executor,
                            files, artifacts, dispatcher, mapper);
                    case YARN -> new YarnSparkExecutionBackend(config.yarn(), executor, files, artifacts, dispatcher);
                };
                values.put(key, new Target(key, config.name() == null || config.name().isBlank() ? key : config.name(),
                        fingerprint(config), backend, config));
            });
        }
        targets = Collections.unmodifiableMap(values);
        readiness = new TargetReadinessCache(targets.size());
        if (!legacy) targets.values().forEach(this::readiness);
    }

    public Collection<Target> all() { return targets.values(); }
    public boolean legacy() { return legacy; }

    public BackendReadiness readiness(Target target) {
        return legacy ? target.backend().readiness() : readiness.get(target.key(), target.backend()::readiness);
    }

    @jakarta.annotation.PreDestroy
    public void close() { readiness.close(); }

    public Target require(String key) {
        if ((key == null || key.isBlank()) && legacy) key = "default";
        Target target = targets.get(key);
        if (target == null) throw new IllegalArgumentException("执行目标不存在、未启用或未指定");
        return target;
    }

    public TaskExecutionBackend forExecution(DispatcherTaskExecution execution) throws BackendException {
        Target target;
        try { target = require(execution.getTargetKey()); }
        catch (IllegalArgumentException exception) { throw new BackendException("BACKEND_TARGET_UNAVAILABLE", "原执行目标当前不可用"); }
        if (target.backend().type() != execution.getBackendType()
                || (!legacy && !Objects.equals(target.fingerprint(), execution.getTargetFingerprint()))
                || (execution.getTargetFingerprint() != null && !target.fingerprint().equals(execution.getTargetFingerprint()))) {
            throw new BackendException("BACKEND_TARGET_CHANGED", "执行目标身份发生变化，禁止改投或清理其他环境");
        }
        return target.backend();
    }

    private static String fingerprint(DispatcherTargetsProperties.Target target) {
        // Credentials, image versions and resource budgets are deliberately excluded.
        String identity = switch (target.backend()) {
            case LOCAL_DOCKER -> "LOCAL_DOCKER\n" + target.localDocker().executable() + "\n"
                    + target.localDocker().absoluteWorkDirectory() + "\n" + System.getenv().getOrDefault("DOCKER_HOST", "local");
            case KUBERNETES -> "KUBERNETES\n" + target.kubernetes().apiServer().replaceFirst("/+$", "") + "\n" + target.kubernetes().namespace();
            case YARN -> "YARN\n" + (target.yarn().clusterId() == null ? target.yarn().yarn() : target.yarn().clusterId())
                    + "\n" + target.yarn().queue() + "\n" + target.yarn().hadoopConfDirectory();
        };
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
