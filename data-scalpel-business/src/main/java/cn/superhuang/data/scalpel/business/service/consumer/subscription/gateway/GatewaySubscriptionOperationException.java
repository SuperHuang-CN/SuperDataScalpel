package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway;

public class GatewaySubscriptionOperationException extends RuntimeException {

    public GatewaySubscriptionOperationException(String message) {
        super(message);
    }

    public GatewaySubscriptionOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
