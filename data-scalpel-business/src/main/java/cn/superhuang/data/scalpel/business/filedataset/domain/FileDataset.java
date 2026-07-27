package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** A logical file dataset that can own multiple physical files and tables. */
@Entity
@Table(name = "ds_file_dataset")
public class FileDataset extends BaseEntity {

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileDatasetType type;

    /** The single validated parsing configuration shared by every table in this dataset. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "parsing_options", nullable = false)
    private String parsingOptions;

    @Column(length = 1000)
    private String description;

    protected FileDataset() {
    }

    private FileDataset(
            UUID directoryId,
            String name,
            FileDatasetType type,
            String parsingOptions,
            String description
    ) {
        this.directoryId = directoryId;
        this.name = normalizeRequired(name, "名称不能为空");
        this.type = requireType(type);
        this.parsingOptions = normalizeRequired(parsingOptions, "解析参数不能为空");
        this.description = normalizeOptional(description);
    }

    public static FileDataset create(
            UUID directoryId,
            String name,
            FileDatasetType type,
            String parsingOptions,
            String description
    ) {
        return new FileDataset(
                directoryId, name, type, parsingOptions, description
        );
    }

    public void update(UUID directoryId, String name, String parsingOptions, String description) {
        this.directoryId = directoryId;
        this.name = normalizeRequired(name, "名称不能为空");
        String normalizedParsingOptions = normalizeRequired(parsingOptions, "解析参数不能为空");
        this.parsingOptions = normalizedParsingOptions;
        this.description = normalizeOptional(description);
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public String getName() {
        return name;
    }

    public FileDatasetType getType() {
        return type;
    }

    public String getParsingOptions() {
        return parsingOptions;
    }

    public String getDescription() {
        return description;
    }

    private static FileDatasetType requireType(FileDatasetType value) {
        if (value == null) {
            throw new IllegalArgumentException("文件数据集类型不能为空");
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
