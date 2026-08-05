package cn.superhuang.superapigateway.controlplane.execution;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.Callable;

@Component
public class ControlPlaneExecutor {

    private final Scheduler scheduler;

    public ControlPlaneExecutor(@Qualifier("controlPlaneScheduler") Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public <T> Mono<T> execute(Callable<T> operation) {
        return Mono.fromCallable(operation).subscribeOn(scheduler);
    }

    public Mono<Void> run(CheckedRunnable operation) {
        return Mono.fromRunnable(() -> {
            try {
                operation.run();
            } catch (Exception exception) {
                throw exception instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException(exception);
            }
        }).subscribeOn(scheduler).then();
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }
}
