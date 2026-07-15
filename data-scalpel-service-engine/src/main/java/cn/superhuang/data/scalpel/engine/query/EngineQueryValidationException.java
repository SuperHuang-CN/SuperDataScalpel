package cn.superhuang.data.scalpel.engine.query;

/** Client-visible validation failure for the fixed standard query protocol. */
public class EngineQueryValidationException extends RuntimeException {

    public EngineQueryValidationException(String message) {
        super(message);
    }

    public EngineQueryValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
