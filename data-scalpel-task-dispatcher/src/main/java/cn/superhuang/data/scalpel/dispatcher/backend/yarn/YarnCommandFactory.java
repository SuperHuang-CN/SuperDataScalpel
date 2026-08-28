package cn.superhuang.data.scalpel.dispatcher.backend.yarn;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.DriverJavaOptions;
import cn.superhuang.data.scalpel.dispatcher.config.YarnProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import cn.superhuang.data.scalpel.contract.execution.SparkConfigurationEntry;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;

public class YarnCommandFactory {
    private static final String RUNNER_MAIN =
            "cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain";
    private final YarnProperties properties;

    public YarnCommandFactory(YarnProperties properties) { this.properties = properties; }

    public List<String> submit(ExecutionIdentity identity, Path launchFile, SparkExecutionResourceSpec resources) {
        List<String> command = new ArrayList<>(List.of(
                properties.sparkSubmit(),
                "--master", "yarn",
                "--deploy-mode", "cluster",
                "--name", "datascalpel-" + identity.executionId(),
                "--class", RUNNER_MAIN,
                "--queue", properties.queue(),
                "--driver-cores", Integer.toString(resources.driverCores()),
                "--driver-memory", resources.driverMemoryMiB() + "m",
                "--executor-memory", resources.executorMemoryMiB() + "m",
                "--executor-cores", Integer.toString(resources.executorCores()),
                "--num-executors", Integer.toString(resources.executorInstances()),
                "--conf", "spark.yarn.tags=" + tag(identity),
                "--conf", "spark.yarn.submit.waitAppCompletion=false",
                "--files", launchFile.toUri() + "#launch.json",
                properties.runnerJar()
        ));
        // User configuration is already allow-listed by Admin; append it after platform defaults.
        // Platform-controlled keys are rejected before this boundary.
        return List.copyOf(command);
    }

    public List<String> submit(ExecutionIdentity identity, Path launchFile, List<SparkConfigurationEntry> sparkConf,
                                SparkExecutionResourceSpec resources) {
        List<String> base = new ArrayList<>(submit(identity, launchFile, resources));
        int jarIndex = base.size() - 1;
        String driverJavaOptions = DriverJavaOptions.extract(sparkConf);
        if (driverJavaOptions != null && !driverJavaOptions.isBlank()) {
            base.add(jarIndex++, "--driver-java-options");
            base.add(jarIndex++, driverJavaOptions);
        }
        for (SparkConfigurationEntry entry : DriverJavaOptions.withoutDriverJavaOptions(sparkConf)) {
            base.add(jarIndex++, "--conf");
            base.add(jarIndex++, entry.name() + "=" + entry.value());
        }
        return List.copyOf(base);
    }

    public List<String> status(String applicationId) {
        return List.of(properties.yarn(), "application", "-status", applicationId);
    }

    public List<String> kill(String applicationId) {
        return List.of(properties.yarn(), "application", "-kill", applicationId);
    }

    public List<String> logs(String applicationId) {
        return List.of(properties.yarn(), "logs", "-applicationId", applicationId);
    }

    public List<String> recover(ExecutionIdentity identity) {
        return List.of(properties.yarn(), "application", "-list", "-appStates", "ALL", "-appTags", tag(identity));
    }

    public List<String> version() { return List.of(properties.sparkSubmit(), "--version"); }

    public List<String> nodes() { return List.of(properties.yarn(), "node", "-list"); }

    public List<String> runnerJarReadable() {
        return List.of(properties.hdfs(), "dfs", "-test", "-r", properties.runnerJar());
    }

    public List<String> javaVersion() { return List.of("java", "-version"); }

    public static String tag(ExecutionIdentity identity) {
        return "datascalpel-execution-" + identity.executionId();
    }
}
