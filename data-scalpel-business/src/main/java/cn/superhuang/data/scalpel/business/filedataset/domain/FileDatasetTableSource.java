package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One currently effective physical-file contribution to a stable logical table. */
@Entity
@Table(
        name = "ds_file_dataset_table_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_file_table_source_file_key",
                columnNames = {"file_dataset_table_id", "source_file_id", "source_key"}
        ),
        indexes = {
                @Index(name = "idx_ds_file_table_source_order", columnList = "file_dataset_table_id,source_order"),
                @Index(name = "idx_ds_file_table_source_file", columnList = "source_file_id")
        }
)
public class FileDatasetTableSource extends BaseEntity {

    @Column(name = "file_dataset_table_id", nullable = false, updatable = false)
    private UUID fileDatasetTableId;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "source_key", nullable = false, length = 255)
    private String sourceKey;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "schema_fingerprint", nullable = false, length = 64)
    private String schemaFingerprint;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "source_metadata", nullable = false)
    private String sourceMetadata;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    protected FileDatasetTableSource() {
    }

    private FileDatasetTableSource(
            UUID tableId,
            UUID sourceFileId,
            String sourceName,
            String sourceKey,
            int sourceOrder,
            long rowCount,
            String schemaFingerprint,
            String sourceMetadata,
            Instant activatedAt
    ) {
        this.fileDatasetTableId = Objects.requireNonNull(tableId, "文件数据集表不能为空");
        replace(sourceFileId, sourceName, sourceKey, rowCount, schemaFingerprint, sourceMetadata, activatedAt);
        setSourceOrder(sourceOrder);
    }

    public static FileDatasetTableSource create(
            UUID tableId,
            UUID sourceFileId,
            String sourceName,
            String sourceKey,
            int sourceOrder,
            long rowCount,
            String schemaFingerprint,
            String sourceMetadata,
            Instant activatedAt
    ) {
        return new FileDatasetTableSource(
                tableId, sourceFileId, sourceName, sourceKey, sourceOrder, rowCount,
                schemaFingerprint, sourceMetadata, activatedAt
        );
    }

    public void replace(
            UUID sourceFileId,
            String sourceName,
            String sourceKey,
            long rowCount,
            String schemaFingerprint,
            String sourceMetadata,
            Instant activatedAt
    ) {
        this.sourceFileId = Objects.requireNonNull(sourceFileId, "来源文件不能为空");
        this.sourceName = required(sourceName, "来源名称不能为空");
        this.sourceKey = required(sourceKey, "来源键不能为空");
        if (rowCount < 0) {
            throw new IllegalArgumentException("记录数不能小于零");
        }
        this.rowCount = rowCount;
        this.schemaFingerprint = required(schemaFingerprint, "Schema 指纹不能为空");
        this.sourceMetadata = required(sourceMetadata, "来源元数据不能为空");
        this.activatedAt = Objects.requireNonNull(activatedAt, "生效时间不能为空");
    }

    public void setSourceOrder(int sourceOrder) {
        if (sourceOrder < 0) {
            throw new IllegalArgumentException("来源顺序不能小于零");
        }
        this.sourceOrder = sourceOrder;
    }

    public void replaceSchema(String schemaFingerprint, String sourceMetadata) {
        this.schemaFingerprint = required(schemaFingerprint, "Schema 指纹不能为空");
        this.sourceMetadata = required(sourceMetadata, "来源元数据不能为空");
    }

    public UUID getFileDatasetTableId() { return fileDatasetTableId; }
    public UUID getSourceFileId() { return sourceFileId; }
    public String getSourceName() { return sourceName; }
    public String getSourceKey() { return sourceKey; }
    public int getSourceOrder() { return sourceOrder; }
    public long getRowCount() { return rowCount; }
    public String getSchemaFingerprint() { return schemaFingerprint; }
    public String getSourceMetadata() { return sourceMetadata; }
    public Instant getActivatedAt() { return activatedAt; }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
