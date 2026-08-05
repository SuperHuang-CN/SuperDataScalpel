package cn.superhuang.superapigateway.controlplane.service;

public class InvalidManagementRequestException extends RuntimeException {

    public InvalidManagementRequestException(String message) {
        super(message);
    }
}
