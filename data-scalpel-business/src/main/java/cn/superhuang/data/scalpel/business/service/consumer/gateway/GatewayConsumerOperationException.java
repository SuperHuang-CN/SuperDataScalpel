package cn.superhuang.data.scalpel.business.service.consumer.gateway;

public class GatewayConsumerOperationException extends RuntimeException {

    public GatewayConsumerOperationException(String message) {
        super(message);
    }

    public GatewayConsumerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
