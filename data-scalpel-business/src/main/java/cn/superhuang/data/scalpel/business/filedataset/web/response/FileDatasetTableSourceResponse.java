package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;

import java.time.Instant;
import java.util.UUID;

public record FileDatasetTableSourceResponse(
        UUID id,
        UUID tableId,
        UUID sourceFileId,
        String sourceName,
        String sourceKey,
        int sourceOrder,
        long rowCount,
        String schemaFingerprint,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetTableSourceResponse from(FileDatasetTableSource source) {
        return new FileDatasetTableSourceResponse(
                source.getId(), source.getFileDatasetTableId(), source.getSourceFileId(),
                source.getSourceName(), source.getSourceKey(), source.getSourceOrder(),
                source.getRowCount(), source.getSchemaFingerprint(), source.getActivatedAt(),
                source.getCreatedAt(), source.getUpdatedAt()
        );
    }
}
