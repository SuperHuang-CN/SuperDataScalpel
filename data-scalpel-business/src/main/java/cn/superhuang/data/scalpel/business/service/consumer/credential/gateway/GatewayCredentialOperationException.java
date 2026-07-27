package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway;

public class GatewayCredentialOperationException extends RuntimeException {

    public GatewayCredentialOperationException(String message) {
        super(message);
    }

    public GatewayCredentialOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
