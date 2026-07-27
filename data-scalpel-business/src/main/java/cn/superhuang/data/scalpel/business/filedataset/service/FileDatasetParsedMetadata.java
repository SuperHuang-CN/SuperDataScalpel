package cn.superhuang.data.scalpel.business.filedataset.service;

import java.util.Map;

/** Compact summary persisted with the latest successful file sample parsing. */
public record FileDatasetParsedMetadata(
        int sampledRecordCount,
        boolean truncated,
        Map<String, Object> sourceMetadata
) {
    public FileDatasetParsedMetadata(int sampledRecordCount, boolean truncated) {
        this(sampledRecordCount, truncated, Map.of());
    }

    public FileDatasetParsedMetadata {
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }
}
