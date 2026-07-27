package cn.superhuang.datascalpel.taskengine.runner;

final class RunnerExecutionException extends RuntimeException {
    private final String code;
    private final String nodeId;

    RunnerExecutionException(String code, String message, String nodeId) {
        super(message);
        this.code = code;
        this.nodeId = nodeId;
    }

    RunnerExecutionException(String code, String message, String nodeId, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.nodeId = nodeId;
    }

    String code() {
        return code;
    }

    String nodeId() {
        return nodeId;
    }
}
