package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.datascalpel.taskengine.config.EngineConfiguration;
import cn.superhuang.datascalpel.taskengine.contract.HealthResponse;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SparkRuntime implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SparkRuntime.class);

    private final SparkSession baseSession;
    private final JavaSparkContext sparkContext;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SparkRuntime(EngineConfiguration configuration) {
        SparkConf sparkConf = new SparkConf();
        configuration.sparkProperties().forEach((key, value) -> {
            if (!sparkConf.contains(key)) {
                sparkConf.set(key, value);
            }
        });
        this.baseSession = SedonaSparkSupport.initialize(
                SedonaSparkSupport.builder()
                        .config(SedonaSparkSupport.configure(sparkConf))
                        .getOrCreate());
        this.sparkContext = JavaSparkContext.fromSparkContext(baseSession.sparkContext());
        log.info("Spark runtime started: version={}, applicationId={}, master={}",
                baseSession.version(), sparkContext.sc().applicationId(), sparkContext.master());
    }

    public SparkCompilationScope openCompilation(UUID requestId) {
        if (!ready()) {
            throw new IllegalStateException("Spark runtime is not ready");
        }
        String jobGroupId = jobGroupId(requestId);
        sparkContext.setJobGroup(jobGroupId, jobGroupId, true);
        return new SparkCompilationScope(SedonaSparkSupport.childSession(baseSession), sparkContext);
    }

    public void cancelCompilation(UUID requestId) {
        if (!closed.get()) {
            sparkContext.cancelJobGroup(jobGroupId(requestId));
        }
    }

    public boolean ready() {
        return !closed.get() && !sparkContext.sc().isStopped();
    }

    public String applicationId() {
        return sparkContext.sc().applicationId();
    }

    public HealthResponse health() {
        return new HealthResponse(
                ready() ? "UP" : "DOWN",
                baseSession.version(),
                applicationId(),
                sparkContext.master()
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Stopping Spark runtime {}", applicationId());
            baseSession.stop();
        }
    }

    private static String jobGroupId(UUID requestId) {
        return "task-compilation-" + requestId;
    }
}
