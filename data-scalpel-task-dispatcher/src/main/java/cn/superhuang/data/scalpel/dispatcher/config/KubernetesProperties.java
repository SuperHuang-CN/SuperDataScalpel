package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.kubernetes")
public record KubernetesProperties(
        String sparkSubmit,
        String kubectl,
        String master,
        String namespace,
        String serviceAccount,
        String image,
        String driverMemory,
        String executorMemory,
        int executorCores,
        int executorInstances,
        Duration submitTimeout,
        Duration commandTimeout,
        int cancelGraceSeconds,
        Path workDirectory
) {
    public KubernetesProperties {
        sparkSubmit = text(sparkSubmit, "/opt/spark/bin/spark-submit");
        kubectl = text(kubectl, "/usr/local/bin/kubectl");
        master = text(master, "k8s://https://kubernetes.default.svc");
        namespace = text(namespace, "datascalpel");
        serviceAccount = text(serviceAccount, "spark-runner");
        image = image == null ? "" : image.trim();
        driverMemory = text(driverMemory, "2g");
        executorMemory = text(executorMemory, "2g");
        executorCores = executorCores < 1 ? 2 : executorCores;
        executorInstances = executorInstances < 1 ? 2 : executorInstances;
        submitTimeout = submitTimeout == null ? Duration.ofMinutes(5) : submitTimeout;
        commandTimeout = commandTimeout == null ? Duration.ofMinutes(2) : commandTimeout;
        cancelGraceSeconds = cancelGraceSeconds < 0 ? 10 : cancelGraceSeconds;
        workDirectory = workDirectory == null ? Path.of("work", "task-executions-kubernetes") : workDirectory;
        if (!master.startsWith("k8s://")) throw new IllegalArgumentException("Kubernetes master必须使用k8s:// URI");
        if (!namespace.matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")) {
            throw new IllegalArgumentException("Kubernetes Namespace格式无效");
        }
        if (submitTimeout.isNegative() || submitTimeout.isZero()
                || commandTimeout.isNegative() || commandTimeout.isZero()) {
            throw new IllegalArgumentException("Kubernetes命令超时必须大于0");
        }
    }

    public Path absoluteWorkDirectory() { return workDirectory.toAbsolutePath().normalize(); }

    public boolean immutableImage() {
        return image.matches("[^\\s]+@sha256:[0-9a-fA-F]{64}");
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
