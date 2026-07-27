package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Test-only Kafka isolation that preserves the production listener lifecycle contract. */
@Component
@Profile("test")
public class TestKafkaListenerManager implements DispatcherListenerManager {
    private boolean running;

    @Override
    public void start(DispatcherRegistration registration) {
        running = true;
    }

    @Override
    public void stopCommandListener() {
        running = false;
    }

    @Override
    public void stopAll() {
        running = false;
    }

    @Override
    public boolean listenersRunning() {
        return running;
    }

    @Override
    public BackendReadiness readiness(DispatcherTopics topics) {
        return BackendReadiness.up();
    }
}
