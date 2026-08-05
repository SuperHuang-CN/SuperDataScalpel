package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
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

/** One ordered field snapshot inside a common-field template. */
@Entity
@Table(
        name = "ds_model_field_template_item",
        indexes = {
                @Index(name = "idx_ds_model_field_template_item_template", columnList = "template_id"),
                @Index(name = "idx_ds_model_field_template_item_dictionary", columnList = "standard_dictionary_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_model_field_template_item_code",
                columnNames = {"template_id", "code"}
        )
)
public class ModelFieldTemplateItem extends BaseEntity {

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

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

    protected ModelFieldTemplateItem() {
    }

    private ModelFieldTemplateItem(
            UUID templateId,
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
            String description,
            UUID standardDictionaryId
    ) {
        this.templateId = templateId;
        update(
                code, name, fieldType, length, precision, scale, geometry,
                nullable, primaryKey, sortOrder, description, standardDictionaryId
        );
    }

    public static ModelFieldTemplateItem create(
            UUID templateId,
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
            String description,
            UUID standardDictionaryId
    ) {
        return new ModelFieldTemplateItem(
                templateId, code, name, fieldType, length, precision, scale, geometry,
                nullable, primaryKey, sortOrder, description, standardDictionaryId
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
            String description,
            UUID standardDictionaryId
    ) {
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
        this.standardDictionaryId = standardDictionaryId;
    }

    public UUID getTemplateId() {
        return templateId;
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
