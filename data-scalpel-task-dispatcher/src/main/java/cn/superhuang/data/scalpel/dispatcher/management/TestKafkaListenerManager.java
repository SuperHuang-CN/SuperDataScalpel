package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Test-only Kafka isolation that preserves the production listener lifecycle contract. */
@Component
@Profile("test")
public class TestKafkaListenerManager implements DispatcherListenerManager {
    private final java.util.Set<java.util.UUID> running = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging;

    public TestKafkaListenerManager(cn.superhuang.data.scalpel.dispatcher.config.DispatcherMessagingProperties messaging) {
        this.messaging = messaging;
    }

    @Override
    public void start(DispatcherRegistration registration) {
        running.add(registration.getEngineId());
    }

    @Override
    public void stopCommandListener() {
        running.clear();
    }

    @Override
    public void stopAll() {
        running.clear();
    }

    @Override
    public boolean listenersRunning() {
        return !running.isEmpty();
    }

    @Override public void stop(java.util.UUID engineId) { if (!messaging.shared()) running.remove(engineId); }
    @Override public boolean listenersRunning(java.util.UUID engineId) { return messaging.shared() ? !running.isEmpty() : running.contains(engineId); }

    @Override
    public BackendReadiness readiness(DispatcherTopics topics) {
        return BackendReadiness.up();
    }
}
