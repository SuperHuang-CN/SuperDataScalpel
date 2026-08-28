package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.DriverJavaOptions;
import cn.superhuang.data.scalpel.dispatcher.config.KubernetesProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;

public class KubernetesCommandFactory {
    private static final String RUNNER_MAIN = "cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain";
    private final KubernetesProperties properties;

    public KubernetesCommandFactory(KubernetesProperties properties) { this.properties = properties; }

    public List<String> submit(ExecutionIdentity identity, SparkExecutionResourceSpec resources) {
        String secret = KubernetesNames.secret(identity);
        List<String> command = new ArrayList<>(List.of(
                properties.sparkSubmit(),
                "--master", properties.master(),
                "--deploy-mode", "cluster",
                "--name", KubernetesNames.application(identity),
                "--class", RUNNER_MAIN,
                "--conf", "spark.kubernetes.namespace=" + properties.namespace(),
                "--conf", "spark.kubernetes.authenticate.driver.serviceAccountName=" + properties.serviceAccount(),
                "--conf", "spark.kubernetes.container.image=" + properties.image(),
                "--conf", "spark.kubernetes.driver.pod.name=" + KubernetesNames.driverPod(identity),
                "--conf", driverLabel(KubernetesNames.MANAGED, "true"),
                "--conf", executorLabel(KubernetesNames.MANAGED, "true"),
                "--conf", driverLabel(KubernetesNames.ENGINE_ID, identity.engineId().toString()),
                "--conf", driverLabel(KubernetesNames.EXECUTION_ID, identity.executionId().toString()),
                "--conf", driverLabel(KubernetesNames.RUN_ID, identity.runId().toString()),
                "--conf", driverLabel(KubernetesNames.ATTEMPT, Integer.toString(identity.attempt())),
                "--conf", executorLabel(KubernetesNames.ENGINE_ID, identity.engineId().toString()),
                "--conf", executorLabel(KubernetesNames.EXECUTION_ID, identity.executionId().toString()),
                "--conf", executorLabel(KubernetesNames.RUN_ID, identity.runId().toString()),
                "--conf", executorLabel(KubernetesNames.ATTEMPT, Integer.toString(identity.attempt())),
                "--conf", "spark.kubernetes.driver.secrets." + secret + "=/opt/datascalpel/runtime",
                "--conf", "spark.kubernetes.driverEnv.DATASCALPEL_TASK_LAUNCH_FILE=/opt/datascalpel/runtime/launch.json",
                "--conf", "spark.kubernetes.driverEnv.DATASCALPEL_TASK_WORK_DIRECTORY=/tmp/datascalpel",
                "--conf", "spark.kubernetes.submission.waitAppCompletion=false",
                "--conf", "spark.driver.cores=" + resources.driverCores(),
                "--conf", "spark.driver.memory=" + resources.driverMemoryMiB() + "m",
                "--conf", "spark.executor.memory=" + resources.executorMemoryMiB() + "m",
                "--conf", "spark.executor.cores=" + resources.executorCores(),
                "--conf", "spark.executor.instances=" + resources.executorInstances(),
                "local:///opt/datascalpel/task-runner-cluster.jar"
        ));
        return List.copyOf(command);
    }

    public List<String> submit(ExecutionIdentity identity, List<SparkConfigurationEntry> sparkConf,
                               SparkExecutionResourceSpec resources) {
        List<String> command = new ArrayList<>(submit(identity, resources));
        int runnerIndex = command.size() - 1;
        String driverJavaOptions = DriverJavaOptions.extract(sparkConf);
        if (driverJavaOptions != null && !driverJavaOptions.isBlank()) {
            command.add(runnerIndex++, "--driver-java-options");
            command.add(runnerIndex++, driverJavaOptions);
        }
        for (SparkConfigurationEntry entry : DriverJavaOptions.withoutDriverJavaOptions(sparkConf)) {
            command.add(runnerIndex++, "--conf");
            command.add(runnerIndex++, entry.name() + "=" + entry.value());
        }
        return List.copyOf(command);
    }

    public List<String> createSecret(ExecutionIdentity identity, Path launchFile) {
        return kubectl("create", "secret", "generic", KubernetesNames.secret(identity),
                "--from-file=launch.json=" + launchFile.toAbsolutePath().normalize());
    }

    public List<String> labelSecret(ExecutionIdentity identity) {
        return kubectl("label", "secret", KubernetesNames.secret(identity), "--overwrite",
                KubernetesNames.MANAGED + "=true",
                KubernetesNames.ENGINE_ID + "=" + identity.engineId(),
                KubernetesNames.EXECUTION_ID + "=" + identity.executionId(),
                KubernetesNames.RUN_ID + "=" + identity.runId(),
                KubernetesNames.ATTEMPT + "=" + identity.attempt());
    }

    public List<String> getSecret(ExecutionIdentity identity) {
        return kubectl("get", "secret", KubernetesNames.secret(identity),
                "-l", KubernetesNames.selector(identity), "-o", "name");
    }

    public List<String> deleteSecret(ExecutionIdentity identity) {
        return kubectl("delete", "secret", KubernetesNames.secret(identity), "--ignore-not-found=true");
    }

    public List<String> getDriver(String podName) { return kubectl("get", "pod", podName, "-o", "json"); }

    public List<String> find(ExecutionIdentity identity) {
        return kubectl("get", "pods", "-l", KubernetesNames.selector(identity), "-o", "json");
    }

    public List<String> deleteDriver(String podName) {
        return kubectl("delete", "pod", podName, "--grace-period=" + properties.cancelGraceSeconds(),
                "--ignore-not-found=true");
    }

    public List<String> deleteExecutors(ExecutionIdentity identity) {
        return kubectl("delete", "pods", "-l", KubernetesNames.selector(identity),
                "--grace-period=" + properties.cancelGraceSeconds(), "--ignore-not-found=true");
    }

    public List<String> forceDeleteDriver(String podName) {
        return kubectl("delete", "pod", podName, "--grace-period=0", "--force",
                "--ignore-not-found=true");
    }

    public List<String> forceDeleteExecutors(ExecutionIdentity identity) {
        return kubectl("delete", "pods", "-l", KubernetesNames.selector(identity),
                "--grace-period=0", "--force", "--ignore-not-found=true");
    }

    public List<String> logs(String podName) { return kubectl("logs", podName, "--timestamps"); }

    public List<String> namespace() { return kubectl("get", "namespace", properties.namespace(), "-o", "name"); }

    public List<String> authCanI(String verb, String resource) {
        return kubectl("auth", "can-i", verb, resource);
    }

    public List<String> version() { return List.of(properties.sparkSubmit(), "--version"); }

    private List<String> kubectl(String... values) {
        List<String> result = new ArrayList<>();
        result.add(properties.kubectl());
        result.add("-n");
        result.add(properties.namespace());
        result.addAll(List.of(values));
        return List.copyOf(result);
    }

    private static String driverLabel(String key, String value) {
        return "spark.kubernetes.driver.label." + key + "=" + value;
    }

    private static String executorLabel(String key, String value) {
        return "spark.kubernetes.executor.label." + key + "=" + value;
    }
}
