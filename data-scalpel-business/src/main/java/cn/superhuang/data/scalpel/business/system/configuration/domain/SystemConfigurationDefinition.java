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
    ),
    TASK_ENGINE_BASE_URL(
            "task.engine.base-url",
            "Task Engine 地址",
            "http://127.0.0.1:18091",
            SystemConfigurationValueType.STRING,
            "Admin 后端访问 Task Engine 的内部基础地址。",
            30
    ),
    FILE_DATASET_PARSING_QUEUE_ENABLED(
            "file-dataset.parsing.queue-enabled",
            "文件数据集解析队列开关",
            "true",
            SystemConfigurationValueType.BOOLEAN,
            "是否允许统一文件解析 Worker 继续领取新任务；关闭不会删除排队任务。",
            100
    ),
    FILE_DATASET_PARSING_WORKER_CONCURRENCY(
            "file-dataset.parsing.worker-concurrency",
            "文件数据集解析并发数",
            "2",
            SystemConfigurationValueType.INTEGER,
            "所有文件类型共享的解析 Worker 并发数，允许 1 到 16。",
            110,
            1,
            16
    ),
    FILE_DATASET_PARSING_MAX_ATTEMPTS(
            "file-dataset.parsing.max-attempts",
            "文件数据集解析最大尝试次数",
            "3",
            SystemConfigurationValueType.INTEGER,
            "新解析任务允许的最大尝试次数，允许 1 到 10。",
            120,
            1,
            10
    ),
    FILE_DATASET_PARSING_RETRY_BASE_DELAY_SECONDS(
            "file-dataset.parsing.retry-base-delay-seconds",
            "文件数据集解析重试基础延时",
            "30",
            SystemConfigurationValueType.INTEGER,
            "可重试故障的指数退避基础秒数，允许 1 到 3600。",
            130,
            1,
            3_600
    ),
    FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS(
            "file-dataset.parsing.history-retention-days",
            "文件数据集解析历史保留天数",
            "30",
            SystemConfigurationValueType.INTEGER,
            "终态解析任务的历史保留天数，允许 1 到 3650。",
            140,
            1,
            3_650
    );

    private final String configKey;
    private final String name;
    private final String defaultValue;
    private final SystemConfigurationValueType valueType;
    private final String description;
    private final int sortOrder;
    private final Integer minimumValue;
    private final Integer maximumValue;

    SystemConfigurationDefinition(
            String configKey,
            String name,
            String defaultValue,
            SystemConfigurationValueType valueType,
            String description,
            int sortOrder
    ) {
        this(configKey, name, defaultValue, valueType, description, sortOrder, null, null);
    }

    SystemConfigurationDefinition(
            String configKey,
            String name,
            String defaultValue,
            SystemConfigurationValueType valueType,
            String description,
            int sortOrder,
            Integer minimumValue,
            Integer maximumValue
    ) {
        this.configKey = configKey;
        this.name = name;
        this.defaultValue = defaultValue;
        this.valueType = valueType;
        this.description = description;
        this.sortOrder = sortOrder;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
    }

    public SystemConfiguration newEntity() {
        return SystemConfiguration.create(configKey, name, normalizeValue(defaultValue), valueType, description, sortOrder);
    }

    public String getConfigKey() {
        return configKey;
    }

    public SystemConfigurationValueType getValueType() {
        return valueType;
    }

    public String normalizeValue(String rawValue) {
        String normalized = valueType.normalize(rawValue);
        if (minimumValue != null && maximumValue != null) {
            int value = Integer.parseInt(normalized);
            if (value < minimumValue || value > maximumValue) {
                throw new IllegalArgumentException(
                        "配置值必须在 " + minimumValue + " 到 " + maximumValue + " 之间"
                );
            }
        }
        return normalized;
    }

    public static java.util.Optional<SystemConfigurationDefinition> findByConfigKey(String configKey) {
        if (configKey == null) {
            return java.util.Optional.empty();
        }
        for (SystemConfigurationDefinition definition : values()) {
            if (definition.configKey.equals(configKey)) {
                return java.util.Optional.of(definition);
            }
        }
        return java.util.Optional.empty();
    }
}
