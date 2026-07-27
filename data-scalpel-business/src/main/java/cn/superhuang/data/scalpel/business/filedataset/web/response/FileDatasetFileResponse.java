package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;

import java.time.Instant;
import java.util.UUID;

/** API projection intentionally excludes the internal object key and storage ETag. */
public record FileDatasetFileResponse(
        UUID id,
        UUID fileDatasetId,
        String originalFileName,
        FileDatasetFormat format,
        FileDatasetCompression compression,
        String contentType,
        long sizeBytes,
        FileDatasetFileStatus status,
        FileDatasetStorageKind storageKind,
        Long materializedSizeBytes,
        Integer materializedEntryCount,
        UUID currentPreparationJobId,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetFileResponse from(FileDatasetFile file) {
        return new FileDatasetFileResponse(
                file.getId(), file.getFileDatasetId(), file.getOriginalFileName(), file.getFormat(), file.getCompression(),
                file.getContentType(), file.getSizeBytes(), file.getStatus(), file.getStorageKind(),
                file.getMaterializedSizeBytes(), file.getMaterializedEntryCount(), file.getCurrentPreparationJobId(),
                file.getCreatedAt(), file.getUpdatedAt()
        );
    }
}
