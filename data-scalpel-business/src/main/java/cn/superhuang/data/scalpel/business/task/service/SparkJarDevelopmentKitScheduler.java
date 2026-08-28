package cn.superhuang.data.scalpel.business.task.service;

import jakarta.annotation.PreDestroy;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
import java.util.UUID;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SparkJarDevelopmentKitScheduler {
    private static final Logger log=LoggerFactory.getLogger(SparkJarDevelopmentKitScheduler.class);
    private final SparkJarDevelopmentKitService service;private final SparkJarDevelopmentKitWorker worker;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).name("spark-jar-kit-worker").factory());private final AtomicBoolean running=new AtomicBoolean();private final String instanceId=UUID.randomUUID().toString();
    public SparkJarDevelopmentKitScheduler(SparkJarDevelopmentKitService service,SparkJarDevelopmentKitWorker worker){this.service=service;this.worker=worker;}
    @Scheduled(fixedDelay=1000) public void schedule(){try{service.recoverExpired();if(running.compareAndSet(false,true))executor.execute(()->{try{while(worker.runOne(instanceId)){} }catch(RuntimeException e){log.warn("Spark JAR 开发包 Worker 异常",e);}finally{running.set(false);}});}catch(RuntimeException e){log.debug("Spark JAR 开发包队列本轮调度失败",e);}}
    @Scheduled(fixedDelay=3600000) public void expire(){service.expireArtifacts();}
    @PreDestroy void close(){executor.shutdown();}
}
