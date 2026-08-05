package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A stable metadata contract for one managed or externally bound physical table. */
@Entity
@Table(
        name = "ds_data_model",
        indexes = @Index(
                name = "idx_ds_data_model_warehouse_layer",
                columnList = "warehouse_layer_id"
        ),
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

    @Column(name = "warehouse_layer_id")
    private UUID warehouseLayerId;

    @Column(name = "storage_data_source_id", nullable = false)
    private UUID storageDataSourceId;

    @Column(name = "catalog_name", length = 128)
    private String catalogName;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "physical_table_name", nullable = false, length = 128)
    private String physicalTableName;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_table_mode", nullable = false, length = 32)
    @ColumnDefault("'MANAGED'")
    private PhysicalTableMode physicalTableMode = PhysicalTableMode.MANAGED;

    /** Comma-separated simple field codes used only by a managed single-node ClickHouse MergeTree table. */
    @Column(name = "clickhouse_order_by_columns", length = 1100)
    private String clickHouseOrderByColumns;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataModelStatus status;

    @Column(name = "schema_version", nullable = false)
    @ColumnDefault("1")
    private int schemaVersion = 1;

    @Column(length = 1000)
    private String description;

    protected DataModel() {
    }

    private DataModel(
            String code,
            String name,
            UUID directoryId,
            UUID warehouseLayerId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            String description
    ) {
        this.code = normalizeCode(code);
        this.status = DataModelStatus.DRAFT;
        update(
                name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName, physicalTableName,
                physicalTableMode, clickHouseOrderByColumns, description
        );
    }

    public static DataModel create(
            String code,
            String name,
            UUID directoryId,
            UUID warehouseLayerId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            String description
    ) {
        return new DataModel(
                code, name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName, physicalTableName,
                physicalTableMode, clickHouseOrderByColumns, description
        );
    }

    public static DataModel create(
            String code,
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            String description
    ) {
        return create(
                code, name, directoryId, null, storageDataSourceId, catalogName, schemaName,
                physicalTableName, physicalTableMode, clickHouseOrderByColumns, description
        );
    }

    public static DataModel create(
            String code,
            String name,
            UUID directoryId,
            UUID warehouseLayerId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            String description
    ) {
        return create(
                code, name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName, physicalTableName,
                physicalTableMode, List.of(), description
        );
    }

    public static DataModel create(
            String code,
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            String description
    ) {
        return create(
                code, name, directoryId, null, storageDataSourceId, catalogName, schemaName,
                physicalTableName, physicalTableMode, List.of(), description
        );
    }

    public void update(
            String name,
            UUID directoryId,
            UUID warehouseLayerId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            String description
    ) {
        this.name = normalizeRequired(name);
        this.directoryId = directoryId;
        this.warehouseLayerId = warehouseLayerId;
        this.storageDataSourceId = storageDataSourceId;
        this.catalogName = normalizeOptional(catalogName);
        this.schemaName = normalizeOptional(schemaName);
        this.physicalTableName = normalizeCode(physicalTableName);
        this.physicalTableMode = physicalTableMode == null ? PhysicalTableMode.MANAGED : physicalTableMode;
        this.clickHouseOrderByColumns = normalizeOrderByColumns(clickHouseOrderByColumns);
        this.description = normalizeOptional(description);
    }

    public void update(
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            String description
    ) {
        update(
                name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName,
                physicalTableName, physicalTableMode, clickHouseOrderByColumns, description
        );
    }

    public void update(
            String name,
            UUID directoryId,
            UUID warehouseLayerId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            String description
    ) {
        update(
                name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName, physicalTableName,
                physicalTableMode, getClickHouseOrderByColumns(), description
        );
    }

    public void update(
            String name,
            UUID directoryId,
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            String description
    ) {
        update(
                name, directoryId, warehouseLayerId, storageDataSourceId, catalogName, schemaName,
                physicalTableName, physicalTableMode, getClickHouseOrderByColumns(), description
        );
    }

    public void publish() {
        status = DataModelStatus.PUBLISHED;
    }

    public void disable() {
        status = DataModelStatus.DISABLED;
    }

    public void advanceSchemaVersion() {
        if (schemaVersion == Integer.MAX_VALUE) {
            throw new IllegalStateException("模型结构版本已达到最大值");
        }
        schemaVersion++;
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

    public UUID getWarehouseLayerId() {
        return warehouseLayerId;
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

    public PhysicalTableMode getPhysicalTableMode() {
        return physicalTableMode == null ? PhysicalTableMode.MANAGED : physicalTableMode;
    }

    public List<String> getClickHouseOrderByColumns() {
        if (clickHouseOrderByColumns == null || clickHouseOrderByColumns.isBlank()) {
            return List.of();
        }
        return List.of(clickHouseOrderByColumns.split(","));
    }

    public DataModelStatus getStatus() {
        return status;
    }

    public int getSchemaVersion() {
        return schemaVersion;
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

    private static String normalizeOrderByColumns(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String code = normalizeCode(value);
            if (!normalized.add(code)) {
                throw new IllegalArgumentException("ClickHouse 排序键字段不能重复：" + value);
            }
        }
        return String.join(",", normalized);
    }
}
