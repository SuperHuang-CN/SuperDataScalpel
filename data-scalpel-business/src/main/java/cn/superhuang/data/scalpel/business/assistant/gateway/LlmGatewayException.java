package cn.superhuang.data.scalpel.business.assistant.gateway;

public class LlmGatewayException extends RuntimeException {

    private final boolean timeout;

    public LlmGatewayException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
