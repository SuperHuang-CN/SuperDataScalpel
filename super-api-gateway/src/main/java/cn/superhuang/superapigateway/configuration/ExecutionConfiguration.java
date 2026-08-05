package cn.superhuang.superapigateway.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutionConfiguration {

    @Bean("controlPlaneTaskExecutor")
    public ThreadPoolTaskExecutor controlPlaneTaskExecutor(SuperApiGatewayProperties properties) {
        var limits = properties.controlPlane();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("gateway-control-");
        executor.setCorePoolSize(limits.coreThreads());
        executor.setMaxPoolSize(limits.maxThreads());
        executor.setQueueCapacity(limits.queueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean("controlPlaneScheduler")
    public Scheduler controlPlaneScheduler(
            @Qualifier("controlPlaneTaskExecutor") TaskExecutor executor
    ) {
        return Schedulers.fromExecutor(executor);
    }

    @Bean(destroyMethod = "shutdown", name = "snapshotReloadExecutor")
    public ExecutorService snapshotReloadExecutor(SuperApiGatewayProperties properties) {
        return Executors.newFixedThreadPool(
                properties.runtime().reloadThreads(),
                Thread.ofPlatform().name("gateway-snapshot-", 0).factory()
        );
    }
}
