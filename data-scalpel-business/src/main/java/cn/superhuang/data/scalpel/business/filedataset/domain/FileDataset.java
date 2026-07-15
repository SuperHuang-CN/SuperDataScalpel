package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** A platform-managed raw file and its future parsing metadata. */
@Entity
@Table(name = "ds_file_dataset")
public class FileDataset extends BaseEntity {

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileDatasetFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileDatasetCompression compression;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /** Relative key below the configured internal object-storage root prefix. */
    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_etag", length = 255)
    private String storageEtag;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private FileDatasetParseStatus parseStatus;

    /** Reserved for a validated format-specific parsing-options JSON document. */
    @Column(name = "parsing_options", length = 8000)
    private String parsingOptions;

    /** Summary JSON for the latest successful sample parsing. */
    @Column(name = "parsed_metadata", length = 8000)
    private String parsedMetadata;

    @Column(name = "parse_error", length = 2000)
    private String parseError;

    @Column(length = 1000)
    private String description;

    protected FileDataset() {
    }

    private FileDataset(
            UUID directoryId,
            String name,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String originalFileName,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag,
            String description
    ) {
        this.directoryId = directoryId;
        this.name = normalizeRequired(name, "名称不能为空");
        this.format = requireFormat(format);
        this.compression = requireCompression(compression);
        this.originalFileName = normalizeRequired(originalFileName, "原始文件名不能为空");
        this.objectKey = normalizeRequired(objectKey, "对象存储 Key 不能为空");
        this.contentType = normalizeOptional(contentType);
        this.sizeBytes = requireSize(sizeBytes);
        this.storageEtag = normalizeOptional(storageEtag);
        this.description = normalizeOptional(description);
        resetParsing();
    }

    public static FileDataset create(
            UUID directoryId,
            String name,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String originalFileName,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag,
            String description
    ) {
        return new FileDataset(
                directoryId, name, format, compression, originalFileName, objectKey, contentType, sizeBytes, storageEtag,
                description
        );
    }

    public void update(UUID directoryId, String name, FileDatasetFormat format, String description) {
        FileDatasetFormat normalizedFormat = requireFormat(format);
        boolean formatChanged = this.format != normalizedFormat;
        this.directoryId = directoryId;
        this.name = normalizeRequired(name, "名称不能为空");
        this.format = normalizedFormat;
        this.description = normalizeOptional(description);
        if (formatChanged) {
            resetParsing();
        }
    }

    public void replaceContent(
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String originalFileName,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag
    ) {
        this.format = requireFormat(format);
        this.compression = requireCompression(compression);
        this.originalFileName = normalizeRequired(originalFileName, "原始文件名不能为空");
        this.objectKey = normalizeRequired(objectKey, "对象存储 Key 不能为空");
        this.contentType = normalizeOptional(contentType);
        this.sizeBytes = requireSize(sizeBytes);
        this.storageEtag = normalizeOptional(storageEtag);
        resetParsing();
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public String getName() {
        return name;
    }

    public FileDatasetFormat getFormat() {
        return format;
    }

    public FileDatasetCompression getCompression() {
        return compression;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageEtag() {
        return storageEtag;
    }

    public FileDatasetParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParsingOptions() {
        return parsingOptions;
    }

    public String getParsedMetadata() {
        return parsedMetadata;
    }

    public String getParseError() {
        return parseError;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasParsingOptions() {
        return parsingOptions != null && !parsingOptions.isBlank();
    }

    public void configureParsing(String parsingOptions) {
        this.parsingOptions = normalizeRequired(parsingOptions, "解析参数不能为空");
        this.parsedMetadata = null;
        this.parseError = null;
        this.parseStatus = FileDatasetParseStatus.UNPARSED;
    }

    public void startParsing() {
        if (!hasParsingOptions()) {
            throw new IllegalStateException("请先保存解析参数");
        }
        this.parsedMetadata = null;
        this.parseError = null;
        this.parseStatus = FileDatasetParseStatus.PARSING;
    }

    public void completeParsing(String parsedMetadata) {
        this.parsedMetadata = normalizeRequired(parsedMetadata, "解析元数据不能为空");
        this.parseError = null;
        this.parseStatus = FileDatasetParseStatus.READY;
    }

    public void failParsing(String parseError) {
        this.parsedMetadata = null;
        this.parseError = normalizeRequired(parseError, "解析错误不能为空");
        this.parseStatus = FileDatasetParseStatus.FAILED;
    }

    private void resetParsing() {
        parseStatus = FileDatasetParseStatus.UNPARSED;
        parsingOptions = null;
        parsedMetadata = null;
        parseError = null;
    }

    private static FileDatasetFormat requireFormat(FileDatasetFormat value) {
        if (value == null) {
            throw new IllegalArgumentException("文件格式不能为空");
        }
        return value;
    }

    private static FileDatasetCompression requireCompression(FileDatasetCompression value) {
        if (value == null) {
            throw new IllegalArgumentException("文件压缩方式不能为空");
        }
        return value;
    }

    private static long requireSize(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("文件大小不能小于零");
        }
        return value;
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
