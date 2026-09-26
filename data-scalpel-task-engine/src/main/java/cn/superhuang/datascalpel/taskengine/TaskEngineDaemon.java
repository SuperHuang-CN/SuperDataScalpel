package cn.superhuang.datascalpel.taskengine;

import cn.superhuang.datascalpel.taskengine.compiler.TaskCompilationService;
import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import cn.superhuang.datascalpel.taskengine.http.TaskEngineHttpServer;
import cn.superhuang.datascalpel.taskengine.spark.SparkRuntime;
import cn.superhuang.datascalpel.taskengine.language.JavaLanguageServer;
import cn.superhuang.datascalpel.taskengine.language.LanguageServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskEngineDaemon implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskEngineDaemon.class);

    private final SparkRuntime sparkRuntime;
    private final TaskCompilationService compilationService;
    private final TaskEngineHttpServer httpServer;
    private final JavaLanguageServer languageServer;
    private final AtomicBoolean closed = new AtomicBoolean();

    private TaskEngineDaemon(EngineConfiguration configuration) throws Exception {
        SparkRuntime newSparkRuntime = new SparkRuntime(configuration);
        TaskCompilationService newCompilationService = null;
        TaskEngineHttpServer newHttpServer = null;
        try {
            newCompilationService = new TaskCompilationService(configuration, newSparkRuntime);
            newHttpServer = new TaskEngineHttpServer(
                    configuration,
                    newSparkRuntime,
                    newCompilationService
            );
            this.sparkRuntime = newSparkRuntime;
            this.compilationService = newCompilationService;
            this.httpServer = newHttpServer;
            this.languageServer = new JavaLanguageServer(configuration,
                    LanguageServiceConfiguration.environment(configuration.port()));
        } catch (Exception exception) {
            if (newHttpServer != null) newHttpServer.close();
            if (newCompilationService != null) newCompilationService.close();
            newSparkRuntime.close();
            throw exception;
        }
    }

    public static void main(String[] args) throws Exception {
        EngineConfiguration configuration = EngineConfiguration.load(args);
        TaskEngineDaemon daemon = new TaskEngineDaemon(configuration);
        Runtime.getRuntime().addShutdownHook(new Thread(daemon::close, "task-engine-shutdown"));
        daemon.httpServer.start();
        daemon.languageServer.start();
        log.info("DataScalpel Task Engine is ready");
        new CountDownLatch(1).await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        log.info("Stopping DataScalpel Task Engine");
        httpServer.close();
        languageServer.close();
        compilationService.close();
        sparkRuntime.close();
    }
}
