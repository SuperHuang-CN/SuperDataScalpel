package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One physical object owned by a logical file dataset. */
@Entity
@Table(
        name = "ds_file_dataset_file",
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_file_dataset_file_object_key", columnNames = "object_key")
)
public class FileDatasetFile extends BaseEntity {

    @Column(name = "file_dataset_id", nullable = false, updatable = false)
    private UUID fileDatasetId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileDatasetFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileDatasetCompression compression;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_etag", length = 255)
    private String storageEtag;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_status", nullable = false, length = 32)
    private FileDatasetFileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_kind", nullable = false, length = 32)
    private FileDatasetStorageKind storageKind;

    @Column(name = "materialized_prefix", length = 512)
    private String materializedPrefix;

    @Column(name = "materialized_size_bytes")
    private Long materializedSizeBytes;

    @Column(name = "materialized_entry_count")
    private Integer materializedEntryCount;

    @Column(name = "current_preparation_job_id")
    private UUID currentPreparationJobId;

    protected FileDatasetFile() {
    }

    private FileDatasetFile(
            UUID fileDatasetId,
            String originalFileName,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag
    ) {
        if (fileDatasetId == null) {
            throw new IllegalArgumentException("文件数据集不能为空");
        }
        this.fileDatasetId = fileDatasetId;
        replace(originalFileName, format, compression, objectKey, contentType, sizeBytes, storageEtag);
    }

    public static FileDatasetFile create(
            UUID fileDatasetId,
            String originalFileName,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag
    ) {
        return new FileDatasetFile(
                fileDatasetId, originalFileName, format, compression, objectKey, contentType, sizeBytes, storageEtag
        );
    }

    public void replace(
            String originalFileName,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageEtag
    ) {
        this.originalFileName = required(originalFileName, "原始文件名不能为空");
        this.format = java.util.Objects.requireNonNull(format, "文件格式不能为空");
        this.compression = java.util.Objects.requireNonNull(compression, "文件压缩方式不能为空");
        this.objectKey = required(objectKey, "对象存储 Key 不能为空");
        this.contentType = optional(contentType);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("文件大小不能小于零");
        }
        this.sizeBytes = sizeBytes;
        this.storageEtag = optional(storageEtag);
        this.storageKind = switch (format) {
            case GDB -> FileDatasetStorageKind.GDB_DIRECTORY;
            case SHP -> FileDatasetStorageKind.SHAPEFILE_COMPONENT_SET;
            default -> FileDatasetStorageKind.SINGLE_OBJECT;
        };
        this.status = requiresPreparation() ? FileDatasetFileStatus.PREPARING : FileDatasetFileStatus.READY;
        this.materializedPrefix = null;
        this.materializedSizeBytes = null;
        this.materializedEntryCount = null;
        this.currentPreparationJobId = null;
    }

    public void queuePreparation(UUID jobId) {
        if (!requiresPreparation()) {
            throw new IllegalStateException("当前文件不需要准备任务");
        }
        if (currentPreparationJobId != null) {
            throw new IllegalStateException("文件已经存在准备任务");
        }
        currentPreparationJobId = java.util.Objects.requireNonNull(jobId, "准备任务不能为空");
        status = FileDatasetFileStatus.PREPARING;
    }

    public void completePreparation(
            UUID jobId,
            String materializedPrefix,
            long materializedSizeBytes,
            int materializedEntryCount
    ) {
        requireCurrentPreparation(jobId);
        if (materializedSizeBytes < 0 || materializedEntryCount < 1) {
            throw new IllegalArgumentException("文件物化统计无效");
        }
        this.materializedPrefix = required(materializedPrefix, "文件物化前缀不能为空");
        this.materializedSizeBytes = materializedSizeBytes;
        this.materializedEntryCount = materializedEntryCount;
        this.status = FileDatasetFileStatus.READY;
        this.currentPreparationJobId = null;
    }

    /** Marks a validated single-object file ready after asynchronous table discovery. */
    public void completePreparationWithoutMaterialization(UUID jobId) {
        requireCurrentPreparation(jobId);
        this.status = FileDatasetFileStatus.READY;
        this.currentPreparationJobId = null;
    }

    public boolean requiresPreparation() {
        return storageKind != FileDatasetStorageKind.SINGLE_OBJECT || format == FileDatasetFormat.GPKG;
    }

    public void cancelQueuedPreparation(UUID jobId) {
        requireCurrentPreparation(jobId);
        currentPreparationJobId = null;
    }

    public UUID getFileDatasetId() {
        return fileDatasetId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public FileDatasetFormat getFormat() {
        return format;
    }

    public FileDatasetCompression getCompression() {
        return compression;
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

    public FileDatasetFileStatus getStatus() {
        return status;
    }

    public FileDatasetStorageKind getStorageKind() {
        return storageKind;
    }

    public String getMaterializedPrefix() {
        return materializedPrefix;
    }

    public Long getMaterializedSizeBytes() {
        return materializedSizeBytes;
    }

    public Integer getMaterializedEntryCount() {
        return materializedEntryCount;
    }

    public UUID getCurrentPreparationJobId() {
        return currentPreparationJobId;
    }

    private void requireCurrentPreparation(UUID jobId) {
        if (jobId == null
                || !jobId.equals(currentPreparationJobId)
                || getStatus() != FileDatasetFileStatus.PREPARING) {
            throw new IllegalStateException("文件准备任务已经过期");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
