package cn.superhuang.datascalpel.taskengine.runner;

final class FileDatasetReadException extends RuntimeException {
    private final String code;
    private final String nodeId;
    private final String nodeName;
    private final boolean retryable;

    FileDatasetReadException(
            String code,
            String message,
            String nodeId,
            String nodeName,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.retryable = retryable;
    }

    String code() {
        return code;
    }

    String nodeId() {
        return nodeId;
    }

    String nodeName() {
        return nodeName;
    }

    boolean retryable() {
        return retryable;
    }
}
