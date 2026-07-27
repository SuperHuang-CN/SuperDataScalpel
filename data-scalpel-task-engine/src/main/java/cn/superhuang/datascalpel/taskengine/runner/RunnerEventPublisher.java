package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;

interface RunnerEventPublisher extends AutoCloseable {
    void publish(RunnerExecutionEvent event) throws Exception;

    @Override
    void close();
}
