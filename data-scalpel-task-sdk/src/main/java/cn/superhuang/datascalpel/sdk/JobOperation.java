package cn.superhuang.datascalpel.sdk;

/**
 * Timed user operation. Closing records timer metrics and restores the previous Spark Job
 * Description. Instances are idempotently closeable on their creating thread.
 */
public interface JobOperation extends AutoCloseable {
    @Override
    void close();
}
