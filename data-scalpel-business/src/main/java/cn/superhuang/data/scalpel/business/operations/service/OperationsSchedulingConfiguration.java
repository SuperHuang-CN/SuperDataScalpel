package cn.superhuang.data.scalpel.business.operations.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class OperationsSchedulingConfiguration {
    @Bean
    public ThreadPoolTaskScheduler operationsScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); scheduler.setThreadNamePrefix("operations-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true); scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
