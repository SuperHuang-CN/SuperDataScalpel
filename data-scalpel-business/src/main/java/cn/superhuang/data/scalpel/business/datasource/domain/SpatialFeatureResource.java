package cn.superhuang.data.scalpel.business.datasource.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;
import java.util.UUID;

/** A registered, schema-pinned ArcGIS layer or WFS FeatureType. */
@Entity
@Table(name = "ds_spatial_feature_resource", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_spatial_feature_resource_source_code", columnNames = {"data_source_id", "code"}
))
public class SpatialFeatureResource extends BaseEntity {

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SpatialServiceProtocol protocol;

    @Column(name = "remote_identifier", nullable = false, length = 1000)
    private String remoteIdentifier;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    protected SpatialFeatureResource() {
    }

    public static SpatialFeatureResource create(
            UUID dataSourceId,
            String code,
            String name,
            SpatialServiceProtocol protocol,
            String remoteIdentifier,
            boolean enabled,
            String definitionJson
    ) {
        SpatialFeatureResource resource = new SpatialFeatureResource();
        resource.dataSourceId = java.util.Objects.requireNonNull(dataSourceId, "数据源不能为空");
        resource.code = required(code).toLowerCase(Locale.ROOT);
        resource.protocol = java.util.Objects.requireNonNull(protocol, "空间服务协议不能为空");
        resource.remoteIdentifier = required(remoteIdentifier);
        resource.update(name, enabled, definitionJson);
        return resource;
    }

    public void update(String name, boolean enabled, String definitionJson) {
        this.name = required(name);
        this.enabled = enabled;
        this.definitionJson = required(definitionJson);
    }

    public UUID getDataSourceId() { return dataSourceId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public SpatialServiceProtocol getProtocol() { return protocol; }
    public String getRemoteIdentifier() { return remoteIdentifier; }
    public boolean isEnabled() { return enabled; }
    public String getDefinitionJson() { return definitionJson; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("字段不能为空");
        return value.trim();
    }
}
