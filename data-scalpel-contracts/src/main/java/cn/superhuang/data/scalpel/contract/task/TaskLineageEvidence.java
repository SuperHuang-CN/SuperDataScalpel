package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

/**
 * Spark-free, producer-neutral lineage evidence emitted by static compilation or a task run.
 * It deliberately contains only stable resource identities and never SQL text or data values.
 */
@JsonClassDescription("由静态编译或任务运行产生的通用血缘证据；只包含稳定资源身份和安全元数据，不包含 SQL 或数据值。")
public record TaskLineageEvidence(
        @JsonPropertyDescription("血缘证据分析状态：COMPLETE 完成，PARTIAL 部分完成，UNAVAILABLE 无法形成可靠证据。")
        AnalysisStatus analysisStatus,
        @JsonPropertyDescription("整体证据粒度：MODEL_ONLY 仅资产级，FIELD_PARTIAL 部分字段可追溯，FIELD_COMPLETE 全部相关字段可追溯。")
        Coverage coverage,
        @JsonPropertyDescription("当前任务定义中可确认的输出数据流列表。")
        List<Flow> flows,
        @JsonPropertyDescription("解释血缘缺失、降级或不完整原因的非阻断告警；没有时为空列表。")
        List<Warning> warnings
) {
    public TaskLineageEvidence {
        flows = flows == null ? List.of() : List.copyOf(flows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static TaskLineageEvidence unavailable(String code, String message) {
        return new TaskLineageEvidence(
                AnalysisStatus.UNAVAILABLE,
                null,
                List.of(),
                List.of(new Warning(code, message, null, null, null))
        );
    }

    public enum AnalysisStatus { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum Coverage { MODEL_ONLY, FIELD_PARTIAL, FIELD_COMPLETE }
    public enum AssetRole { INPUT, OUTPUT }
    public enum AssetKind { MODEL, JDBC_TABLE, EXTERNAL_RESOURCE }
    public enum ExternalResourceType {
        KAFKA_TOPIC,
        FILE_DATASET_TABLE,
        HTTP_API_RESOURCE,
        SPATIAL_SERVICE_RESOURCE,
        OBJECT_STORAGE_PATH,
        JDBC_QUERY_RESULT
    }
    public enum WriteMode { APPEND, FULL_OVERWRITE, UPSERT, SNAPSHOT_SYNC, CREATE_NEW }
    public enum OutputEffect {
        DERIVED,
        WRITTEN_UNKNOWN_SOURCE,
        CONSTANT,
        DEFAULT_VALUE,
        NULL_FILLED,
        PRESERVED,
        NOT_WRITTEN
    }
    public enum DerivationType { DIRECT, CALCULATED, AGGREGATED }
    public enum UsageType { JOIN_KEY, FILTER_CONDITION, GROUP_KEY, SORT_KEY, PARTITION_KEY }

    @JsonClassDescription("围绕一个输出生产者形成的数据流及其输入资产、输出资产、字段关系和局部告警。")
    public record Flow(
            @JsonPropertyDescription("当前证据快照内稳定标识一个输出数据流的键，不是数据库 UUID。")
            String flowKey,
            @JsonPropertyDescription("生成该输出 Schema 或血缘结果的稳定生产者键；通常对应任务定义中的输出或转换节点，不是数据库 UUID。")
            String producerKey,
            @JsonPropertyDescription("产生当前逻辑表的任务节点类型编码；用于解释 producerKey，不表示资产类型。")
            String producerType,
            @JsonPropertyDescription("该输出流的证据粒度：仅资产级、部分字段级或完整字段级。")
            Coverage coverage,
            @JsonPropertyDescription("该数据流最终写入的模型、JDBC 表或外部资源；无可确认输出时为空。")
            Asset outputAsset,
            @JsonPropertyDescription("该数据流读取的输入资产列表。")
            List<Asset> inputAssets,
            @JsonPropertyDescription("该输出流涉及的输入和输出字段证据；通过 localAssetKey 与资产关联，并通过 localFieldKey 被字段关系引用。")
            List<Field> fields,
            @JsonPropertyDescription("来源字段到目标字段的直接、计算或聚合派生关系。")
            List<FieldEdge> fieldEdges,
            @JsonPropertyDescription("字段作为连接键、过滤条件、分组键、排序键或分区键的非输出用途。")
            List<FieldUsage> fieldUsages,
            @JsonPropertyDescription("仅影响该输出流血缘完整度的告警；没有时为空列表。")
            List<Warning> warnings
    ) {
        public Flow {
            inputAssets = inputAssets == null ? List.of() : List.copyOf(inputAssets);
            fields = fields == null ? List.of() : List.copyOf(fields);
            fieldEdges = fieldEdges == null ? List.of() : List.copyOf(fieldEdges);
            fieldUsages = fieldUsages == null ? List.of() : List.copyOf(fieldUsages);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    @JsonClassDescription("通用血缘流中的输入或输出资产，可表示纳管模型、JDBC 表或受控外部资源。")
    public record Asset(
            @JsonPropertyDescription("本次血缘快照内稳定引用该资产的键；只保证在当前证据中一致，不是数据库 UUID。")
            String localAssetKey,
            @JsonPropertyDescription("资产在该流中的角色：INPUT 为读取来源，OUTPUT 为写入目标。")
            AssetRole role,
            @JsonPropertyDescription("资产种类：MODEL 使用模型字段，JDBC_TABLE 使用数据库表定位，EXTERNAL_RESOURCE 使用外部资源类型和安全标识。")
            AssetKind kind,
            @JsonPropertyDescription("EXTERNAL_RESOURCE 的具体种类；MODEL 和 JDBC_TABLE 资产为空。")
            ExternalResourceType externalResourceType,
            @JsonPropertyDescription("OUTPUT 资产的写入语义，例如追加、全量覆盖、更新插入、快照同步或新建；输入资产为空。")
            WriteMode writeMode,
            @JsonPropertyDescription("MODEL 资产对应的纳管模型 UUID；其他资产类型为空。")
            UUID modelId,
            @JsonPropertyDescription("MODEL 资产对应模型在本次证据中的 Schema 版本；其他资产类型为空。")
            Integer modelSchemaVersion,
            @JsonPropertyDescription("JDBC_TABLE 或 EXTERNAL_RESOURCE 所属数据源 UUID；MODEL 资产或无法解析数据源时为空。")
            UUID dataSourceId,
            @JsonPropertyDescription("数据库 Catalog；不适用时为空。")
            String catalogName,
            @JsonPropertyDescription("数据库 Schema；不适用时为空。")
            String schemaName,
            @JsonPropertyDescription("JDBC_TABLE 对应的数据库物理表名；其他资产类型为空。")
            String physicalTableName,
            @JsonPropertyDescription("HTTP API、空间资源或文件数据集表等已纳管外部资源 UUID；模型、JDBC 表、Kafka Topic 和对象存储路径通常为空。")
            UUID resourceId,
            @JsonPropertyDescription("外部资源稳定键的 SHA-256，用于关联且不暴露原始敏感标识。")
            String resourceKeyHash,
            @JsonPropertyDescription("经过裁剪和脱敏的资产展示名称；无法安全生成时为空，不可作为稳定标识。")
            String safeDisplayName
    ) {
    }

    @JsonClassDescription("通用血缘快照中的一个输入或输出字段，以及任务对输出字段产生的处理效果。")
    public record Field(
            @JsonPropertyDescription("字段所属资产的 localAssetKey。")
            String localAssetKey,
            @JsonPropertyDescription("本次血缘快照内字段的稳定本地键。")
            String localFieldKey,
            @JsonPropertyDescription("能够解析到纳管模型字段时返回字段 UUID；外部字段或历史定义无法解析时为空。")
            UUID modelFieldId,
            @JsonPropertyDescription("模型字段编码或外部列的稳定安全编码；无法确认时为空。")
            String columnCode,
            @JsonPropertyDescription("字段在所属资产中的物理名称；无法确认时为空。")
            String columnName,
            @JsonPropertyDescription("字段在所属资产 Schema 中的零基顺序。")
            int ordinal,
            @JsonPropertyDescription("任务对输出字段的处理结果，区分派生、未知来源、常量、默认值、补空、保留和未写入。")
            OutputEffect outputEffect
    ) {
    }

    @JsonClassDescription("使用当前通用血缘快照本地键指向一个确定字段。")
    public record FieldReference(
            @JsonPropertyDescription("被引用字段所属资产的 localAssetKey。")
            String localAssetKey,
            @JsonPropertyDescription("被引用字段的 localFieldKey。")
            String localFieldKey
    ) {
    }

    @JsonClassDescription("通用血缘中一个来源字段到目标字段的直接、计算或聚合派生关系。")
    public record FieldEdge(
            @JsonPropertyDescription("字段派生关系的来源字段引用。")
            FieldReference source,
            @JsonPropertyDescription("字段派生关系的目标字段引用。")
            FieldReference target,
            @JsonPropertyDescription("当前快照内稳定标识该字段派生关系的键。")
            String derivationKey,
            @JsonPropertyDescription("字段值的派生方式，例如直接、计算或聚合。")
            DerivationType derivationType,
            @JsonPropertyDescription("产生该字段关系的转换节点稳定键。")
            String transformNodeKey
    ) {
    }

    @JsonClassDescription("通用血缘中字段在连接、过滤、分组、排序或分区计算里的非输出用途。")
    public record FieldUsage(
            @JsonPropertyDescription("被任务用作连接、过滤、分组、排序或分区条件的字段引用。")
            FieldReference field,
            @JsonPropertyDescription("使用该字段的转换节点稳定键。")
            String nodeKey,
            @JsonPropertyDescription("字段在计算中的用途类型。")
            UsageType usageType
    ) {
    }

    @JsonClassDescription("不会阻止证据生成、但会降低通用血缘覆盖度或可靠性的结构化告警。")
    public record Warning(
            @JsonPropertyDescription("稳定血缘告警码，用于程序识别降级或证据缺失原因。")
            String code,
            @JsonPropertyDescription("血缘证据降级、缺失或无法确认的可读原因。")
            String message,
            @JsonPropertyDescription("告警关联的 producerKey；全局告警为空。")
            String producerKey,
            @JsonPropertyDescription("告警所属输出数据流的稳定键；全局告警为空。")
            String flowKey,
            @JsonPropertyDescription("告警关联输出字段在结果 Schema 中的零基顺序；非字段告警为空。")
            Integer outputOrdinal
    ) {
    }
}
