package cn.superhuang.data.scalpel.business.panorama.service;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** One in-process worker for the V1 single-Admin deployment. */
@Component
public class PanoramaWorker {
    private final PanoramaService service;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "panorama-worker"); thread.setDaemon(true); return thread;
    });
    public PanoramaWorker(PanoramaService service) { this.service = service; }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        service.recover();
        executor.scheduleWithFixedDelay(() -> {
            try { service.processNext(); service.cleanup(); }
            catch (Exception e) { LoggerFactory.getLogger(PanoramaWorker.class).error("Panorama worker iteration failed", e); }
        }, 0, 1, TimeUnit.SECONDS);
    }
    @PreDestroy public void stop() {
        executor.shutdown();
        try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
