package cn.superhuang.data.scalpel.business.system.configuration.domain;

/**
 * The configuration keys currently understood by the application.
 *
 * <p>Adding a key here makes startup insert it when it is missing. Existing
 * values are never overwritten.</p>
 */
public enum SystemConfigurationDefinition {
    PLATFORM_NAME(
            "platform.name",
            "平台名称",
            "DataScalpel",
            SystemConfigurationValueType.STRING,
            "显示在前端左侧品牌和顶部标题。",
            10
    ),
    PLATFORM_SUBTITLE(
            "platform.subtitle",
            "平台副标题",
            "内网部署 · 模块化单体",
            SystemConfigurationValueType.STRING,
            "显示在前端顶部标题右侧。",
            20
    );

    private final String configKey;
    private final String name;
    private final String defaultValue;
    private final SystemConfigurationValueType valueType;
    private final String description;
    private final int sortOrder;

    SystemConfigurationDefinition(
            String configKey,
            String name,
            String defaultValue,
            SystemConfigurationValueType valueType,
            String description,
            int sortOrder
    ) {
        this.configKey = configKey;
        this.name = name;
        this.defaultValue = defaultValue;
        this.valueType = valueType;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public SystemConfiguration newEntity() {
        return SystemConfiguration.create(configKey, name, defaultValue, valueType, description, sortOrder);
    }

    public String getConfigKey() {
        return configKey;
    }
}
