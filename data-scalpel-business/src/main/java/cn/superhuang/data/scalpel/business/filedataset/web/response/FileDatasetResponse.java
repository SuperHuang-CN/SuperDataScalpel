package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;

import java.time.Instant;
import java.util.UUID;

/** API projection intentionally excludes internal object keys and storage identifiers. */
public record FileDatasetResponse(
        UUID id,
        UUID directoryId,
        String name,
        FileDatasetFormat format,
        FileDatasetCompression compression,
        String originalFileName,
        String contentType,
        long sizeBytes,
        FileDatasetParseStatus parseStatus,
        boolean parsingConfigured,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetResponse from(FileDataset dataset) {
        return new FileDatasetResponse(
                dataset.getId(), dataset.getDirectoryId(), dataset.getName(), dataset.getFormat(), dataset.getCompression(),
                dataset.getOriginalFileName(), dataset.getContentType(), dataset.getSizeBytes(), dataset.getParseStatus(),
                dataset.hasParsingOptions(), dataset.getDescription(), dataset.getCreatedAt(), dataset.getUpdatedAt()
        );
    }
}
