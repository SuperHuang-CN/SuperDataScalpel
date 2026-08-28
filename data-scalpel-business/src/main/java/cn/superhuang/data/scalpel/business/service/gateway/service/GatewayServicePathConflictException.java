package cn.superhuang.data.scalpel.business.service.gateway.service;

/** A requested public gateway path is already owned by another route. */
public class GatewayServicePathConflictException extends GatewayServiceOperationException {

    public GatewayServicePathConflictException(String message) {
        super(message);
    }

    public GatewayServicePathConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
