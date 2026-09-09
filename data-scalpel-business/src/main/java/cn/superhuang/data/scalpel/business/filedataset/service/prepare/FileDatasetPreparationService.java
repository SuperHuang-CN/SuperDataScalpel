package cn.superhuang.data.scalpel.business.filedataset.service.prepare;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;

import java.io.IOException;
import java.util.List;

/** Prepares one archive-backed file into an immutable parser-facing object prefix. */
public interface FileDatasetPreparationService {

    boolean supports(FileDatasetFormat format);

    FileDatasetPreparationResult prepare(FileDatasetPreparationInput input) throws IOException;

    void discard(String materializedPrefix);

    record FileDatasetPreparationInput(
            FileDatasetFormat format,
            String rawObjectKey,
            long rawSizeBytes,
            String materializedPrefix,
            String parsingOptions
    ) {
        public FileDatasetPreparationInput {
            if (format == null) {
                throw new IllegalArgumentException("文件格式不能为空");
            }
            rawObjectKey = requireText(rawObjectKey, "原始归档对象 Key 不能为空");
            materializedPrefix = requireText(materializedPrefix, "文件物化前缀不能为空");
            parsingOptions = requireText(parsingOptions, "文件解析参数不能为空");
            if (rawSizeBytes < 0) {
                throw new IllegalArgumentException("原始归档大小不能小于零");
            }
        }
    }

    record FileDatasetPreparationResult(
            String materializedPrefix,
            long materializedSizeBytes,
            int materializedEntryCount,
            List<DiscoveredTable> tables
    ) {
        public FileDatasetPreparationResult {
            materializedPrefix = materializedPrefix == null || materializedPrefix.isBlank() ? null : materializedPrefix.trim();
            if (materializedSizeBytes < 0 || materializedEntryCount < 0
                    || (materializedPrefix != null && materializedEntryCount < 1)
                    || (materializedPrefix == null && (materializedSizeBytes != 0 || materializedEntryCount != 0))) {
                throw new IllegalArgumentException("文件物化统计无效");
            }
            tables = List.copyOf(tables);
            if (tables.isEmpty()) {
                throw new IllegalArgumentException("文件准备结果必须包含逻辑表");
            }
        }
    }

    record DiscoveredTable(
            String sourceKey,
            String sourceName,
            int sourceOrder
    ) {
        public DiscoveredTable {
            sourceKey = requireText(sourceKey, "来源键不能为空");
            sourceName = requireText(sourceName, "来源名称不能为空");
            if (sourceOrder < 0) {
                throw new IllegalArgumentException("来源顺序不能小于零");
            }
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
