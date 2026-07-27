package cn.superhuang.datascalpel.taskengine.http;

public final class TaskEngineException extends RuntimeException {
    private final int status;
    private final String code;
    private final String title;

    public TaskEngineException(int status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public TaskEngineException(int status, String code, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
