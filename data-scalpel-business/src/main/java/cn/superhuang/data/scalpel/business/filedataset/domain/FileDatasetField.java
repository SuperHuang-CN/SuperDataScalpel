package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
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
                columnNames = {"file_dataset_table_id", "field_name"}
        )
)
public class FileDatasetField extends BaseEntity {

    @Column(name = "file_dataset_table_id", nullable = false, updatable = false)
    private UUID fileDatasetTableId;

    @Column(name = "field_name", nullable = false, length = 255)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "logical_type", nullable = false, length = 32)
    private PlatformDataType fieldType;

    @Column
    private Integer length;

    @Column
    private Integer precision;

    @Column
    private Integer scale;

    @Enumerated(EnumType.STRING)
    @Column(name = "geometry_kind", length = 32)
    private GeometryKind geometryKind;

    @Column(name = "crs_authority", length = 16)
    private String crsAuthority;

    @Column(name = "crs_code")
    private Integer crsCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "coordinate_dimension", length = 16)
    private CoordinateDimension coordinateDimension;

    @Column(nullable = false)
    private boolean nullable;

    protected FileDatasetField() {
    }

    private FileDatasetField(
            UUID fileDatasetTableId,
            String name,
            int sortOrder,
            PlatformTypeDefinition type,
            boolean nullable
    ) {
        if (fileDatasetTableId == null) {
            throw new IllegalArgumentException("文件数据集表不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("字段顺序不能小于零");
        }
        if (type == null) {
            throw new IllegalArgumentException("字段平台类型不能为空");
        }
        this.fileDatasetTableId = fileDatasetTableId;
        this.name = name.trim();
        this.sortOrder = sortOrder;
        this.fieldType = type.type();
        this.length = type.length();
        this.precision = type.precision();
        this.scale = type.scale();
        GeometryTypeDefinition geometry = type.geometry();
        this.geometryKind = geometry == null ? null : geometry.kind();
        this.crsAuthority = geometry == null ? null : geometry.crs().authority();
        this.crsCode = geometry == null ? null : geometry.crs().code();
        this.coordinateDimension = geometry == null ? null : geometry.dimension();
        this.nullable = nullable;
    }

    public static FileDatasetField create(
            UUID fileDatasetTableId,
            String name,
            int sortOrder,
            PlatformTypeDefinition type,
            boolean nullable
    ) {
        return new FileDatasetField(fileDatasetTableId, name, sortOrder, type, nullable);
    }

    public UUID getFileDatasetTableId() {
        return fileDatasetTableId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
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

    public PlatformTypeDefinition getTypeDefinition() {
        GeometryTypeDefinition geometry = geometryKind == null
                ? null
                : new GeometryTypeDefinition(
                        geometryKind,
                        new CrsReference(crsAuthority, crsCode),
                        coordinateDimension
                );
        return new PlatformTypeDefinition(fieldType, length, precision, scale, geometry);
    }

    public GeometryTypeDefinition getGeometry() {
        return getTypeDefinition().geometry();
    }

    public boolean isNullable() {
        return nullable;
    }
}
