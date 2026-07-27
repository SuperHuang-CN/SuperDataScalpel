package cn.superhuang.data.scalpel.dispatcher.backend.yarn;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.config.YarnProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class YarnCommandFactory {
    private static final String RUNNER_MAIN =
            "cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain";
    private final YarnProperties properties;

    public YarnCommandFactory(YarnProperties properties) { this.properties = properties; }

    public List<String> submit(ExecutionIdentity identity, Path launchFile) {
        List<String> command = new ArrayList<>(List.of(
                properties.sparkSubmit(),
                "--master", "yarn",
                "--deploy-mode", "cluster",
                "--name", "datascalpel-" + identity.executionId(),
                "--class", RUNNER_MAIN,
                "--queue", properties.queue(),
                "--driver-memory", properties.driverMemory(),
                "--executor-memory", properties.executorMemory(),
                "--executor-cores", Integer.toString(properties.executorCores()),
                "--num-executors", Integer.toString(properties.numExecutors()),
                "--conf", "spark.yarn.tags=" + tag(identity),
                "--conf", "spark.yarn.submit.waitAppCompletion=false",
                "--files", launchFile.toUri() + "#launch.json",
                properties.runnerJar()
        ));
        return List.copyOf(command);
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
