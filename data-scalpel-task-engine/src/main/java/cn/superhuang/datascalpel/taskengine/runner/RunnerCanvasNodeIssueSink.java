package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeIssueSink;

final class RunnerCanvasNodeIssueSink implements CanvasNodeIssueSink {
    private final String nodeId;

    RunnerCanvasNodeIssueSink(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void error(String code, String message, String path) {
        throw new RunnerExecutionException(code, message, nodeId);
    }

    @Override
    public void warning(String code, String message, String path) {
        // The mandatory preflight already reported this warning. Runtime execution does not duplicate it.
    }

    @Override
    public boolean hasErrors() {
        return false;
    }
}
