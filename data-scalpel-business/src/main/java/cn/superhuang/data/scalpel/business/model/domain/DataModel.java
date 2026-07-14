package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

/** A stable metadata contract for one future physical table. */
@Entity
@Table(
        name = "ds_data_model",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ds_data_model_code", columnNames = "code"),
                @UniqueConstraint(
                        name = "uk_ds_data_model_physical_location",
                        columnNames = {"storage_data_source_id", "catalog_name", "schema_name", "physical_table_name"}
                )
        }
)
public class DataModel extends BaseEntity {

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(name = "storage_data_source_id", nullable = false)
    private UUID storageDataSourceId;

    @Column(name = "catalog_name", length = 128)
    private String catalogName;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "physical_table_name", nullable = false, length = 128)
    private String physicalTableName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataModelStatus status;

    @Column(length = 1000)
    private String description;

    protected DataModel() {
    }

    private DataModel(
            String code,
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            String description
    ) {
        this.code = normalizeCode(code);
        this.status = DataModelStatus.DRAFT;
        update(name, directoryId, storageDataSourceId, catalogName, schemaName, physicalTableName, description);
    }

    public static DataModel create(
            String code,
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            String description
    ) {
        return new DataModel(
                code, name, directoryId, storageDataSourceId, catalogName, schemaName, physicalTableName, description
        );
    }

    public void update(
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            String description
    ) {
        this.name = normalizeRequired(name);
        this.directoryId = directoryId;
        this.storageDataSourceId = storageDataSourceId;
        this.catalogName = normalizeOptional(catalogName);
        this.schemaName = normalizeOptional(schemaName);
        this.physicalTableName = normalizeCode(physicalTableName);
        this.description = normalizeOptional(description);
    }

    public void publish() {
        status = DataModelStatus.PUBLISHED;
    }

    public void disable() {
        status = DataModelStatus.DISABLED;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public UUID getStorageDataSourceId() {
        return storageDataSourceId;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getPhysicalTableName() {
        return physicalTableName;
    }

    public DataModelStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    private static String normalizeCode(String value) {
        return normalizeRequired(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequired(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("必填内容不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
