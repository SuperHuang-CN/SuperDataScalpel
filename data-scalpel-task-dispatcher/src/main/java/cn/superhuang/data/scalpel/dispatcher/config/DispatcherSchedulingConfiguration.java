package cn.superhuang.data.scalpel.dispatcher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Each blocking lane owns one thread; periodic work never queues unbounded concurrent copies. */
@Configuration
public class DispatcherSchedulingConfiguration {
    @Bean
    public ThreadPoolTaskScheduler dispatcherAdmissionScheduler() {
        return scheduler("dispatcherAdmissionScheduler-");
    }

    @Bean
    public ThreadPoolTaskScheduler dispatcherObservationScheduler() {
        return scheduler("dispatcherObservationScheduler-");
    }

    @Bean
    public ThreadPoolTaskScheduler dispatcherCleanupScheduler() {
        return scheduler("dispatcherCleanupScheduler-");
    }

    @Bean
    public ThreadPoolTaskScheduler dispatcherOutboxScheduler() {
        return scheduler("dispatcherOutboxScheduler-");
    }

    private static ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
