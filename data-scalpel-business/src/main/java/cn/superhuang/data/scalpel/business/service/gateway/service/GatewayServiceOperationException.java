package cn.superhuang.data.scalpel.business.service.gateway.service;

public class GatewayServiceOperationException extends RuntimeException {

    public GatewayServiceOperationException(String message) {
        super(message);
    }

    public GatewayServiceOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
