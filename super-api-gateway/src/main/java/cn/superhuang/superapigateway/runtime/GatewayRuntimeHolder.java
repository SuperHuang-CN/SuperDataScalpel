package cn.superhuang.superapigateway.runtime;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class GatewayRuntimeHolder {

    private final AtomicReference<GatewayRuntimeSnapshot> snapshot =
            new AtomicReference<>(GatewayRuntimeSnapshot.empty());
    private final AtomicReference<RuntimeStatus> status =
            new AtomicReference<>(new RuntimeStatus("STARTING", null));

    public GatewayRuntimeSnapshot snapshot() {
        return snapshot.get();
    }

    public RuntimeStatus status() {
        return status.get();
    }

    public void install(GatewayRuntimeSnapshot newSnapshot) {
        snapshot.set(newSnapshot);
        status.set(new RuntimeStatus("READY", null));
    }

    public void failed(Throwable failure) {
        status.set(new RuntimeStatus("DEGRADED", safeMessage(failure)));
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    public record RuntimeStatus(String state, String lastError) {
    }
}
