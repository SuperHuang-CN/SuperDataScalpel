package cn.superhuang.data.scalpel.business.system.configuration.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A code-declared system setting with a database-managed current value. */
@Entity
@Table(
        name = "sys_configuration",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_configuration_config_key", columnNames = "config_key")
)
public class SystemConfiguration extends BaseEntity {

    @Column(name = "config_key", nullable = false, updatable = false, length = 120)
    private String configKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "config_value", nullable = false, length = 4000)
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SystemConfigurationValueType valueType;

    @Column(length = 1000)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected SystemConfiguration() {
    }

    private SystemConfiguration(
            String configKey,
            String name,
            String configValue,
            SystemConfigurationValueType valueType,
            String description,
            int sortOrder
    ) {
        this.configKey = configKey;
        this.name = name;
        this.configValue = valueType.normalize(configValue);
        this.valueType = valueType;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public static SystemConfiguration create(
            String configKey,
            String name,
            String configValue,
            SystemConfigurationValueType valueType,
            String description,
            int sortOrder
    ) {
        return new SystemConfiguration(configKey, name, configValue, valueType, description, sortOrder);
    }

    public void updateValue(String value) {
        configValue = valueType.normalize(value);
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getName() {
        return name;
    }

    public String getConfigValue() {
        return configValue;
    }

    public SystemConfigurationValueType getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
