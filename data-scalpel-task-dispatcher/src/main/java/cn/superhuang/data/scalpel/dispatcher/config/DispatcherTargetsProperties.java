package cn.superhuang.data.scalpel.dispatcher.config;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deployment-owned targets; legacy single-backend mode requires an explicit compatibility flag. */
@ConfigurationProperties(prefix = "data-scalpel.dispatcher")
public record DispatcherTargetsProperties(Map<String, Target> targets, int maxConcurrentSubmissions,
                                         int maintenanceConcurrency) {
    public DispatcherTargetsProperties {
        targets = targets == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        if (targets.size() > 32) throw new IllegalArgumentException("单实例最多配置 32 个执行目标");
        targets.forEach((key, value) -> {
            if (!key.matches("[a-z][a-z0-9-]{0,62}") || value == null) {
                throw new IllegalArgumentException("执行目标键必须是 1～63 位小写字母、数字或连字符，以字母开头");
            }
            if (value.enabled() && value.backend() == ExecutionBackendType.YARN
                    && (value.yarn().hadoopConfDirectory() == null || value.yarn().clusterId() == null)) {
                throw new IllegalArgumentException("多目标 YARN 必须声明独立 hadoop-conf-directory 和稳定 cluster-id");
            }
        });
        maxConcurrentSubmissions = maxConcurrentSubmissions == 0 ? 4 : maxConcurrentSubmissions;
        maintenanceConcurrency = maintenanceConcurrency == 0 ? 8 : maintenanceConcurrency;
        if (maxConcurrentSubmissions < 1 || maxConcurrentSubmissions > 64
                || maintenanceConcurrency < 2 || maintenanceConcurrency > 64) {
            throw new IllegalArgumentException("实例提交并发须为 1～64，维护并发须为 2～64");
        }
    }

    public record Target(Boolean enabled, String name, ExecutionBackendType backend,
                         LocalDockerProperties localDocker, YarnProperties yarn,
                         KubernetesProperties kubernetes, DispatcherStreamingProperties streaming,
                         @org.springframework.boot.context.properties.bind.Name("resource-policy") ResourcePolicy resourcePolicyConfiguration) {
        public Target(Boolean enabled, String name, ExecutionBackendType backend,
                      LocalDockerProperties localDocker, YarnProperties yarn,
                      KubernetesProperties kubernetes, DispatcherStreamingProperties streaming) {
            this(enabled, name, backend, localDocker, yarn, kubernetes, streaming, (ResourcePolicy) null);
        }
        public Target(Boolean enabled, String name, ExecutionBackendType backend,
                      LocalDockerProperties localDocker, YarnProperties yarn,
                      KubernetesProperties kubernetes, DispatcherStreamingProperties streaming,
                      SparkExecutionResourcePolicy policy) {
            this(enabled, name, backend, localDocker, yarn, kubernetes, streaming,
                    policy == null ? null : new ResourcePolicy(Resources.from(policy.defaults()), Resources.from(policy.maximums())));
        }
        public SparkExecutionResourcePolicy resourcePolicy() {
            return resourcePolicyConfiguration == null ? SparkExecutionResourcePolicy.defaultsFor(backend)
                    : resourcePolicyConfiguration.resolve(backend);
        }
        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public Target {
            enabled = enabled == null || enabled;
            if (resourcePolicyConfiguration != null && backend != null) resourcePolicyConfiguration.resolve(backend);
            if (enabled && (backend == null || switch (backend) {
                case LOCAL_DOCKER -> localDocker == null;
                case YARN -> yarn == null;
                case KUBERNETES -> kubernetes == null;
            })) throw new IllegalArgumentException("启用的执行目标必须配置后端类型及对应连接配置");
        }
    }

    /** Deployment binding permits omitted Executor settings only for Local Docker. */
    public record ResourcePolicy(Resources defaults, Resources maximums) {
        SparkExecutionResourcePolicy resolve(ExecutionBackendType backend) {
            if (defaults == null || maximums == null) throw new IllegalArgumentException("资源策略必须包含 defaults 和 maximums");
            return new SparkExecutionResourcePolicy(defaults.resolve(backend), maximums.resolve(backend));
        }
    }

    public record Resources(Integer driverCores, Integer driverMemoryMiB, Integer executorInstances,
                            Integer executorCores, Integer executorMemoryMiB) {
        static Resources from(SparkExecutionResourceSpec value) {
            return new Resources(value.driverCores(), value.driverMemoryMiB(), value.executorInstances(),
                    value.executorCores(), value.executorMemoryMiB());
        }
        SparkExecutionResourceSpec resolve(ExecutionBackendType backend) {
            if (driverCores == null || driverMemoryMiB == null) throw new IllegalArgumentException("资源策略必须配置 Driver CPU 和内存");
            if (backend == ExecutionBackendType.LOCAL_DOCKER) {
                return new SparkExecutionResourceSpec(driverCores, driverMemoryMiB, 1, 1, 1024);
            }
            if (executorInstances == null || executorCores == null || executorMemoryMiB == null) {
                throw new IllegalArgumentException("集群资源策略必须配置 Executor 数量、CPU 和内存");
            }
            return new SparkExecutionResourceSpec(driverCores, driverMemoryMiB, executorInstances, executorCores, executorMemoryMiB);
        }
    }
}
