package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One field inferred from the latest successful file sample parsing. */
@Entity
@Table(
        name = "ds_file_dataset_field",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_file_dataset_field_name",
                columnNames = {"file_dataset_id", "field_name"}
        )
)
public class FileDatasetField extends BaseEntity {

    @Column(name = "file_dataset_id", nullable = false, updatable = false)
    private UUID fileDatasetId;

    @Column(name = "field_name", nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_type", nullable = false, length = 32)
    private LogicalType logicalType;

    @Column(nullable = false)
    private boolean nullable;

    protected FileDatasetField() {
    }

    private FileDatasetField(UUID fileDatasetId, String name, int sortOrder, LogicalType logicalType, boolean nullable) {
        if (fileDatasetId == null) {
            throw new IllegalArgumentException("文件数据集不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("字段顺序不能小于零");
        }
        if (logicalType == null) {
            throw new IllegalArgumentException("字段逻辑类型不能为空");
        }
        this.fileDatasetId = fileDatasetId;
        this.name = name.trim();
        this.sortOrder = sortOrder;
        this.logicalType = logicalType;
        this.nullable = nullable;
    }

    public static FileDatasetField create(
            UUID fileDatasetId,
            String name,
            int sortOrder,
            LogicalType logicalType,
            boolean nullable
    ) {
        return new FileDatasetField(fileDatasetId, name, sortOrder, logicalType, nullable);
    }

    public UUID getFileDatasetId() {
        return fileDatasetId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LogicalType getLogicalType() {
        return logicalType;
    }

    public boolean isNullable() {
        return nullable;
    }
}
