package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;

public interface DispatcherListenerManager {
    void start(DispatcherRegistration registration);
    void stopCommandListener();
    void stopAll();
    boolean listenersRunning();
    BackendReadiness readiness(DispatcherTopics topics);
}
