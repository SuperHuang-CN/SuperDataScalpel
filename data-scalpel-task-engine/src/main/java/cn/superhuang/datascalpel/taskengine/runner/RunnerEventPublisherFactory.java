package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;

@FunctionalInterface
interface RunnerEventPublisherFactory {
    RunnerEventPublisher create(TaskExecutionLaunchDescriptor launch);
}
