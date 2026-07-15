package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

/** One ordered field in a data model definition. */
@Entity
@Table(
        name = "ds_data_model_field",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_data_model_field_code",
                columnNames = {"model_id", "code"}
        )
)
public class DataModelField extends BaseEntity {

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Convert(converter = PlatformDataTypeConverter.class)
    @Column(name = "field_type", nullable = false, length = 32)
    private PlatformDataType fieldType;

    @Column(name = "field_length")
    private Integer length;

    @Column(name = "numeric_precision")
    private Integer precision;

    @Column(name = "numeric_scale")
    private Integer scale;

    @Column(nullable = false)
    private boolean nullable;

    @Column(name = "primary_key", nullable = false)
    private boolean primaryKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 500)
    private String description;

    protected DataModelField() {
    }

    private DataModelField(
            UUID modelId,
            String code,
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        this.modelId = modelId;
        update(code, name, fieldType, length, precision, scale, nullable, primaryKey, sortOrder, description);
    }

    public static DataModelField create(
            UUID modelId,
            String code,
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        return new DataModelField(
                modelId, code, name, fieldType, length, precision, scale,
                nullable, primaryKey, sortOrder, description
        );
    }

    public void update(
            String code,
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        this.code = normalizeRequired(code).toLowerCase(Locale.ROOT);
        this.name = normalizeRequired(name);
        this.fieldType = fieldType;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.sortOrder = sortOrder;
        this.description = normalizeOptional(description);
    }

    public UUID getModelId() {
        return modelId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PlatformDataType getFieldType() {
        return fieldType;
    }

    public Integer getLength() {
        return length;
    }

    public Integer getPrecision() {
        return precision;
    }

    public Integer getScale() {
        return scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getDescription() {
        return description;
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
