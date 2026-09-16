package cn.superhuang.data.scalpel.business.task.execution.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Each blocking lane owns one thread; periodic work never queues unbounded concurrent copies. */
@Configuration
public class ExecutionSchedulingConfiguration {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("taskScheduler-");
    }

    @Bean
    public ThreadPoolTaskScheduler executionOutboxScheduler() {
        return scheduler("executionOutboxScheduler-");
    }

    @Bean
    public ThreadPoolTaskScheduler executionReconciliationScheduler() {
        return scheduler("executionReconciliationScheduler-");
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
