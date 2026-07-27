package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.StreamingQueryState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingSinkType;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;

import java.time.Instant;
import java.util.UUID;

public record TaskStreamingQueryResponse(
        UUID id,
        UUID outputNodeId,
        String outputNodeName,
        StreamingSinkType sinkType,
        String checkpointKey,
        StreamingQueryState state,
        Long latestBatchId,
        Long latestInputRows,
        Double inputRowsPerSecond,
        Double processedRowsPerSecond,
        Long batchDurationMillis,
        Instant lastProgressAt,
        Instant lastErrorAt,
        String lastError
) {
    public static TaskStreamingQueryResponse from(TaskStreamingQuery query) {
        return new TaskStreamingQueryResponse(
                query.getId(), query.getOutputNodeId(), query.getOutputNodeName(),
                query.getSinkType(), query.getCheckpointKey(), query.getState(),
                query.getLatestBatchId(), query.getLatestInputRows(),
                query.getInputRowsPerSecond(), query.getProcessedRowsPerSecond(),
                query.getBatchDurationMillis(), query.getLastProgressAt(),
                query.getLastErrorAt(), query.getLastError()
        );
    }
}
