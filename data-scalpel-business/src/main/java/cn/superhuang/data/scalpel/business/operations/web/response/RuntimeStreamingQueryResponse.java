package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.task.domain.*;
import java.time.Instant;
import java.util.UUID;
public record RuntimeStreamingQueryResponse(UUID id, String name, StreamingQueryState state, Long batchId, Long inputRows,
    Double inputRowsPerSecond, Double processedRowsPerSecond, Long batchDurationMillis, Instant lastProgressAt) {
    public static RuntimeStreamingQueryResponse from(TaskStreamingQuery q) {
        return new RuntimeStreamingQueryResponse(q.getId(), q.getOutputNodeName(), q.getState(), q.getLatestBatchId(),
                q.getLatestInputRows(), q.getInputRowsPerSecond(), q.getProcessedRowsPerSecond(), q.getBatchDurationMillis(), q.getLastProgressAt());
    }
}
