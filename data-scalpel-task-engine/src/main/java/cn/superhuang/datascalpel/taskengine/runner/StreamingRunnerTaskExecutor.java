package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;

@FunctionalInterface
interface StreamingRunnerTaskExecutor {
    void execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            TaskExecutionLaunchDescriptor launch,
            RunnerEventPublisher publisher
    );
}
