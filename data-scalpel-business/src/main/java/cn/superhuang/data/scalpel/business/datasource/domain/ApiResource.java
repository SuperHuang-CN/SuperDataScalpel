package cn.superhuang.data.scalpel.business.datasource.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "ds_api_resource", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_api_resource_source_code", columnNames = {"data_source_id", "code"}
))
public class ApiResource extends BaseEntity {

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "connector_type", nullable = false, length = 100)
    private String connectorType;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    protected ApiResource() {
    }

    public static ApiResource create(
            UUID dataSourceId,
            String code,
            String name,
            String connectorType,
            boolean enabled,
            String definitionJson
    ) {
        ApiResource resource = new ApiResource();
        resource.dataSourceId = dataSourceId;
        resource.code = required(code).toLowerCase(Locale.ROOT);
        resource.update(name, connectorType, enabled, definitionJson);
        return resource;
    }

    public void update(String name, String connectorType, boolean enabled, String definitionJson) {
        this.name = required(name);
        this.connectorType = required(connectorType);
        this.enabled = enabled;
        this.definitionJson = required(definitionJson);
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("字段不能为空");
        }
        return value.trim();
    }
}
