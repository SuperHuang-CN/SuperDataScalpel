package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

/** One ordered field in a data model definition. */
@Entity
@Table(
        name = "ds_data_model_field",
        indexes = @Index(
                name = "idx_ds_data_model_field_standard_dictionary",
                columnList = "standard_dictionary_id"
        ),
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

    @Enumerated(EnumType.STRING)
    @Column(name = "geometry_kind", length = 32)
    private GeometryKind geometryKind;

    @Column(name = "crs_authority", length = 16)
    private String crsAuthority;

    @Column(name = "crs_code")
    private Integer crsCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "coordinate_dimension", length = 8)
    private CoordinateDimension coordinateDimension;

    @Column(nullable = false)
    private boolean nullable;

    @Column(name = "primary_key", nullable = false)
    private boolean primaryKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 500)
    private String description;

    @Column(name = "standard_dictionary_id")
    private UUID standardDictionaryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_column_role", length = 16)
    private DataModelPhysicalColumnRole physicalColumnRole;

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
            GeometryTypeDefinition geometry,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        this.modelId = modelId;
        update(code, name, fieldType, length, precision, scale, geometry, nullable, primaryKey, sortOrder, description);
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
        return create(
                modelId, code, name, fieldType, length, precision, scale, null,
                nullable, primaryKey, sortOrder, description
        );
    }

    public static DataModelField create(
            UUID modelId,
            String code,
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            GeometryTypeDefinition geometry,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        return new DataModelField(
                modelId, code, name, fieldType, length, precision, scale, geometry,
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
        update(
                code, name, fieldType, length, precision, scale, null,
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
            GeometryTypeDefinition geometry,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
        validateGeometry(fieldType, geometry, primaryKey);
        this.code = normalizeRequired(code).toLowerCase(Locale.ROOT);
        this.name = normalizeRequired(name);
        this.fieldType = fieldType;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.geometryKind = geometry == null ? null : geometry.kind();
        this.crsAuthority = geometry == null ? null : geometry.crs().authority();
        this.crsCode = geometry == null ? null : geometry.crs().code();
        this.coordinateDimension = geometry == null ? null : geometry.dimension();
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

    public GeometryTypeDefinition getGeometry() {
        if (geometryKind == null || crsAuthority == null || crsCode == null || coordinateDimension == null) {
            return null;
        }
        return new GeometryTypeDefinition(
                geometryKind,
                new CrsReference(crsAuthority, crsCode),
                coordinateDimension
        );
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

    public UUID getStandardDictionaryId() {
        return standardDictionaryId;
    }

    public void assignStandardDictionary(UUID standardDictionaryId) {
        this.standardDictionaryId = standardDictionaryId;
    }

    public DataModelPhysicalColumnRole getPhysicalColumnRole() {
        return physicalColumnRole == null ? DataModelPhysicalColumnRole.REGULAR : physicalColumnRole;
    }

    public void assignPhysicalColumnRole(DataModelPhysicalColumnRole physicalColumnRole) {
        this.physicalColumnRole = physicalColumnRole == null
                ? DataModelPhysicalColumnRole.REGULAR
                : physicalColumnRole;
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

    private static void validateGeometry(
            PlatformDataType fieldType,
            GeometryTypeDefinition geometry,
            boolean primaryKey
    ) {
        if (fieldType == PlatformDataType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("Geometry 字段必须指定几何类型、CRS 和坐标维度");
            }
            if (!"EPSG".equals(geometry.crs().authority())) {
                throw new IllegalArgumentException("Geometry 字段第一版只支持 EPSG CRS");
            }
            if (geometry.dimension() != CoordinateDimension.XY) {
                throw new IllegalArgumentException("Geometry 字段第一版只支持 XY 二维坐标");
            }
            if (primaryKey) {
                throw new IllegalArgumentException("Geometry 字段不能作为主键");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("只有 Geometry 字段可以设置空间类型定义");
        }
    }
}
