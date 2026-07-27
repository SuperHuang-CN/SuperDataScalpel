package cn.superhuang.datascalpel.taskengine.httpapi;

@FunctionalInterface
public interface HttpApiPullBatchConsumer {
    void accept(HttpApiPullBatch batch);
}
