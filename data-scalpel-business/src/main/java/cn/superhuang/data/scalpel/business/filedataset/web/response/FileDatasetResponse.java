package cn.superhuang.data.scalpel.business.filedataset.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;

import java.time.Instant;
import java.util.UUID;

/** Dataset projection; physical object metadata is exposed by the nested files API. */
public record FileDatasetResponse(
        UUID id,
        UUID directoryId,
        String name,
        FileDatasetType type,
        FileDatasetParsingOptionsResponse parsingOptions,
        long fileCount,
        long tableCount,
        long readyTableCount,
        boolean parsingOptionsLocked,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileDatasetResponse from(
            FileDataset dataset,
            FileDatasetParsingOptionsResponse parsingOptions,
            long fileCount,
            long tableCount,
            long readyTableCount,
            boolean parsingOptionsLocked
    ) {
        return new FileDatasetResponse(
                dataset.getId(), dataset.getDirectoryId(), dataset.getName(), dataset.getType(), parsingOptions,
                fileCount, tableCount, readyTableCount, parsingOptionsLocked,
                dataset.getDescription(), dataset.getCreatedAt(), dataset.getUpdatedAt()
        );
    }
}
