package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher.yarn")
public record YarnProperties(
        String sparkSubmit,
        String yarn,
        String hdfs,
        String deployMode,
        String queue,
        String runnerJar,
        String driverMemory,
        String executorMemory,
        int executorCores,
        int numExecutors,
        Duration submitTimeout,
        Duration commandTimeout,
        Path workDirectory
) {
    public YarnProperties {
        sparkSubmit = text(sparkSubmit, "/opt/spark/bin/spark-submit");
        yarn = text(yarn, "/opt/hadoop/bin/yarn");
        hdfs = text(hdfs, "/opt/hadoop/bin/hdfs");
        deployMode = text(deployMode, "cluster").toLowerCase();
        queue = text(queue, "default");
        runnerJar = text(runnerJar, "hdfs:///datascalpel/runner/task-runner-cluster.jar");
        driverMemory = text(driverMemory, "2g");
        executorMemory = text(executorMemory, "2g");
        executorCores = executorCores < 1 ? 2 : executorCores;
        numExecutors = numExecutors < 1 ? 2 : numExecutors;
        submitTimeout = submitTimeout == null ? Duration.ofMinutes(5) : submitTimeout;
        commandTimeout = commandTimeout == null ? Duration.ofMinutes(2) : commandTimeout;
        workDirectory = workDirectory == null ? Path.of("work", "task-executions-yarn") : workDirectory;
        if (!"cluster".equals(deployMode)) throw new IllegalArgumentException("YARN只支持 cluster deploy mode");
        if (!runnerJar.startsWith("hdfs://") && !runnerJar.startsWith("viewfs://")) {
            throw new IllegalArgumentException("YARN Runner JAR必须使用 HDFS/ViewFS URI");
        }
        if (submitTimeout.isNegative() || submitTimeout.isZero()
                || commandTimeout.isNegative() || commandTimeout.isZero()) {
            throw new IllegalArgumentException("YARN命令超时必须大于0");
        }
    }

    public Path absoluteWorkDirectory() { return workDirectory.toAbsolutePath().normalize(); }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
