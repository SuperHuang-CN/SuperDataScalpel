package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;

import java.util.function.Consumer;

@FunctionalInterface
interface RunnerTaskExecutor {
    TaskExecutionResult execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            Consumer<String> sparkStarted
    );
}
