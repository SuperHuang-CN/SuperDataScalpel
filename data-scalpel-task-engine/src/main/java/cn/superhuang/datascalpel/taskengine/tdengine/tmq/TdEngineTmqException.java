package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

public final class TdEngineTmqException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public TdEngineTmqException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public TdEngineTmqException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
