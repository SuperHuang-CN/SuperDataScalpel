package cn.superhuang.data.scalpel.business.task.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TmqConsumerGroupCleanupScheduler {
    private static final int MAX_PER_RUN = 16;
    private final TmqConsumerGroupCleanupService service;

    public TmqConsumerGroupCleanupScheduler(TmqConsumerGroupCleanupService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${data-scalpel.task.tmq-cleanup-interval:30s}")
    public void run() {
        for (int index = 0; index < MAX_PER_RUN && service.runOne(); index++) {
            // Drain a bounded number of due cleanups without monopolizing the scheduler thread.
        }
    }
}
