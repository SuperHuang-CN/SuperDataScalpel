package cn.superhuang.superapigateway.controlplane.service;

public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}
