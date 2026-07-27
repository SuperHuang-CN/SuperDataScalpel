package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;

import java.time.Instant;
import java.util.UUID;

public record FileDatasetParseJobResponse(
        UUID id,
        FileDatasetParseJobType type,
        UUID fileDatasetId,
        String fileDatasetName,
        UUID sourceFileId,
        String sourceFileName,
        UUID fileDatasetTableId,
        String tableName,
        FileDatasetTableSourceLoadMode loadMode,
        UUID targetSourceId,
        String sourceName,
        String sourceKey,
        FileDatasetParseJobStatus status,
        int attemptCount,
        int maxAttempts,
        Instant availableAt,
        Instant queuedAt,
        Instant startedAt,
        Instant completedAt,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant lastHeartbeatAt,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetParseJobResponse from(FileDatasetParseJob job) {
        return new FileDatasetParseJobResponse(
                job.getId(), job.getType(), job.getFileDatasetId(), job.getDatasetNameSnapshot(),
                job.getSourceFileId(), job.getFileNameSnapshot(), job.getFileDatasetTableId(),
                job.getTableNameSnapshot(), job.getLoadMode(), job.getTargetSourceId(),
                job.getSourceName(), job.getSourceKey(), job.getStatus(), job.getAttemptCount(),
                job.getMaxAttempts(), job.getAvailableAt(), job.getQueuedAt(), job.getStartedAt(),
                job.getCompletedAt(), job.getLeaseOwner(), job.getLeaseExpiresAt(),
                job.getLastHeartbeatAt(), job.getErrorMessage(), job.getCreatedAt(), job.getUpdatedAt()
        );
    }
}
