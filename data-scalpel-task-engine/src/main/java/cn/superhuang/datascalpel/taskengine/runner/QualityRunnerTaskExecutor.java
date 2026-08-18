package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;

import java.nio.file.Path;
import java.util.function.Consumer;

@FunctionalInterface
interface QualityRunnerTaskExecutor {
    TaskExecutionResult execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            Consumer<String> sparkStarted,
            TaskExecutionLaunchDescriptor launch,
            Path workDirectory,
            RunnerArtifactAccess artifactAccess
    );
}
