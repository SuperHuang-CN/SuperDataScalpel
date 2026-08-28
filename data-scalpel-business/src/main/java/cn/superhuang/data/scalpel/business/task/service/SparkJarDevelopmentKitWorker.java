package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitJob;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStage;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SparkJarDevelopmentKitWorker {
    private static final Logger log=LoggerFactory.getLogger(SparkJarDevelopmentKitWorker.class);
    private final SparkJarDevelopmentKitService service;private final SparkJarDevelopmentKitGenerator generator;private final TaskRunArtifactStorage storage;
    public SparkJarDevelopmentKitWorker(SparkJarDevelopmentKitService service,SparkJarDevelopmentKitGenerator generator,TaskRunArtifactStorage storage){this.service=service;this.generator=generator;this.storage=storage;}
    public boolean runOne(String workerId){Optional<SparkJarDevelopmentKitJob> claimed=service.claimNext(workerId);if(claimed.isEmpty())return false;SparkJarDevelopmentKitJob job=claimed.get();String key=null;
        ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("spark-jar-kit-heartbeat").factory());
        heartbeat.scheduleWithFixedDelay(()->{try{service.heartbeat(job.getId(),workerId);}
            catch(RuntimeException exception){log.debug("Spark JAR 开发包心跳失败，jobId={}",job.getId(),exception);}},20,20,TimeUnit.SECONDS);
        try(var kit=generator.generate(job,(stage,percent,model)->service.progress(job.getId(),workerId,stage,percent,model))){service.progress(job.getId(),workerId,SparkJarDevelopmentKitStage.UPLOADING,95,null);key="tasks/%s/spark-jar-development-kits/%s.zip".formatted(job.getTaskId(),job.getId());storage.store(key,kit.zip(),"application/zip");service.succeed(job.getId(),workerId,key,"datascalpel-spark-job-development-kit.zip",kit.size(),kit.sha256());return true;}
        catch(SparkJarDevelopmentKitGenerator.KitGenerationException exception){if(key!=null)try{storage.delete(key);}catch(RuntimeException cleanup){exception.addSuppressed(cleanup);}service.fail(job.getId(),workerId,exception.code(),exception.getMessage(),exception.retryable());return true;}
        catch(DatabaseAccessException exception){boolean retryable="NETWORK_ERROR".equals(exception.code())||"DATABASE_ERROR".equals(exception.code());service.fail(job.getId(),workerId,exception.code(),exception.getMessage(),retryable);return true;}
        catch(RuntimeException|java.io.IOException exception){if(key!=null)try{storage.delete(key);}catch(RuntimeException cleanup){exception.addSuppressed(cleanup);}log.warn("Spark JAR 开发包生成失败，jobId={}",job.getId(),exception);service.fail(job.getId(),workerId,"TEMPORARY_GENERATION_FAILURE","开发包生成遇到临时故障",true);return true;}
        finally{heartbeat.shutdownNow();}}
}
