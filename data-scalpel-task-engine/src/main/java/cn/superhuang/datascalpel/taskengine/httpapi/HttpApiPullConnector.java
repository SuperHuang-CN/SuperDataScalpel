package cn.superhuang.datascalpel.taskengine.httpapi;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.util.Objects;

public interface HttpApiPullConnector {
    String type();

    HttpApiPullResult pull(HttpApiContracts.PullRequest request);

    /**
     * Pulls converted rows synchronously in bounded batches. Connectors that only implement the
     * legacy full-result method remain compatible, but do not gain bounded-memory behavior.
     */
    default HttpApiPullSummary pullBatches(
            HttpApiContracts.PullRequest request,
            HttpApiPullBatchConsumer consumer
    ) {
        Objects.requireNonNull(consumer, "consumer");
        HttpApiPullResult result = pull(request);
        if (result.rows().isEmpty()) {
            return new HttpApiPullSummary(result.pages(), 0, 0, result.responseBytes());
        }
        consumer.accept(new HttpApiPullBatch(1, 0, result.rows()));
        return new HttpApiPullSummary(result.pages(), 1, result.rows().size(), result.responseBytes());
    }
}
