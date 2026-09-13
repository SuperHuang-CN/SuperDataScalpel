package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("流式 TDengine TMQ 输入配置；订阅一个外部管理的完整超级表数据 Topic，并以保存的数据库、超级表和结构指纹防止来源漂移。输出为包含超级表普通字段与 TAG 的无界表，不追加 Topic、子表、VGroup 或 Offset 技术列。")
public record TdEngineTmqInputConfiguration(
        @JsonPropertyDescription("必填的 TDengine 数据源 UUID 字符串；必须对应已启用、具有 SOURCE 用途和 TMQ_SUBSCRIBE 能力的 TDENGINE_WEBSOCKET 数据源，RESTful 类型不支持。")
        String dataSourceId,
        @JsonPropertyDescription("必填的外部 TMQ Topic 名称；必须是单一超级表完整 * 投影的纯数据 Topic，不支持数据库 Topic、子表、投影、过滤、JOIN、聚合或 WITH META。")
        String topicName,
        @JsonPropertyDescription("必填的 Topic 来源数据库 Catalog 名称；必须与元数据快照及当前 Topic 定义一致。")
        String catalogName,
        @JsonPropertyDescription("必填的 Topic 来源超级表名；必须与元数据快照及当前 Topic 定义一致。")
        String supertableName,
        @JsonPropertyDescription("必填的 64 位小写 SHA-256 Topic/超级表定义指纹；发布、重新启用和运行准备时与当前定义复核，变化时拒绝运行，旧版合法指纹会给出升级提示。")
        String topicDefinitionFingerprint,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("首次启动位置：EARLIEST 从各 VGroup 当前最早 Offset 开始，LATEST 从当前末尾开始；省略或 NULL 在构造时规范化为 EARLIEST。已有 Spark Checkpoint 时始终由其中的 VGroup Offset 接管。")
        TdEngineTmqStartingOffsets startingOffsets,
        @JsonPropertyDescription("每个 VGroup 单次微批允许推进的最大 Offset 跨度，范围 1 至 1000000；省略或 NULL 默认 10000。Offset 跨度不承诺等于消息行数。")
        Integer maxOffsetsPerVGroupPerTrigger,
        @JsonPropertyDescription("必填的流式微批触发间隔秒数，范围 1 至 300；旧便利构造器默认 10。实时任务只允许一个无界输入，其值决定任务触发间隔。")
        Integer triggerIntervalSeconds,
        @JsonPropertyDescription("可选的超级表 TIMESTAMP 字段名，用作输出 Schema 事件时间并调用 Spark withWatermark；会先 trim。NULL 或空白表示禁用，并且 watermarkDelaySeconds 也必须为 NULL。平台不会自动猜测事件时间。")
        String eventTimeColumn,
        @JsonPropertyDescription("事件时间 Watermark 延迟秒数，范围 1 至 2592000；必须与非空 eventTimeColumn 同时配置，禁用事件时间时必须为 NULL。")
        Integer watermarkDelaySeconds
) {
    public static final int DEFAULT_MAX_OFFSETS_PER_VGROUP_PER_TRIGGER = 10_000;
    public static final int MAX_OFFSETS_PER_VGROUP_PER_TRIGGER = 1_000_000;
    public static final int DEFAULT_TRIGGER_INTERVAL_SECONDS = 10;
    public static final int MAX_WATERMARK_DELAY_SECONDS = 2_592_000;

    public TdEngineTmqInputConfiguration {
        startingOffsets = startingOffsets == null ? TdEngineTmqStartingOffsets.EARLIEST : startingOffsets;
        maxOffsetsPerVGroupPerTrigger = maxOffsetsPerVGroupPerTrigger == null
                ? DEFAULT_MAX_OFFSETS_PER_VGROUP_PER_TRIGGER
                : maxOffsetsPerVGroupPerTrigger;
        eventTimeColumn = eventTimeColumn == null ? null : eventTimeColumn.trim();
    }

    public TdEngineTmqInputConfiguration(
            String dataSourceId,
            String topicName,
            String catalogName,
            String supertableName,
            String topicDefinitionFingerprint,
            String outputTableName,
            TdEngineTmqStartingOffsets startingOffsets,
            Integer maxOffsetsPerVGroupPerTrigger,
            Integer triggerIntervalSeconds
    ) {
        this(
                dataSourceId, topicName, catalogName, supertableName,
                topicDefinitionFingerprint, outputTableName, startingOffsets,
                maxOffsetsPerVGroupPerTrigger, triggerIntervalSeconds, null, null
        );
    }

    public TdEngineTmqInputConfiguration(
            String dataSourceId,
            String topicName,
            String catalogName,
            String supertableName,
            String topicDefinitionFingerprint,
            String outputTableName,
            TdEngineTmqStartingOffsets startingOffsets,
            Integer maxOffsetsPerVGroupPerTrigger
    ) {
        this(
                dataSourceId, topicName, catalogName, supertableName,
                topicDefinitionFingerprint, outputTableName, startingOffsets,
                maxOffsetsPerVGroupPerTrigger, DEFAULT_TRIGGER_INTERVAL_SECONDS, null, null
        );
    }
}
