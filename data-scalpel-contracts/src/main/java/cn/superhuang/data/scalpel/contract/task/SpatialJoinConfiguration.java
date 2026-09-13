package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理两表空间连接配置。左表是目标要素，右表是连接要素；INNER 只保留匹配目标，Canvas 4.56 起 LEFT 可保留全部目标。可使用至多 8 个有方向的 Sedona 空间谓词；Canvas 4.55 起可再配置至多 8 个属性等值条件，Canvas 4.59 起可增加一项时间关系，Canvas 4.60 起可增加空间 Near/Near Geodesic 及一对多距离输出，全部活动条件按 AND 组合。Canvas 4.57 可显式声明 JOIN_ONE_TO_MANY；Canvas 4.58 起可通过 oneToOne 配置 JOIN_ONE_TO_ONE 汇总或确定性保留规则。Canvas 4.54 起可显式选择、改名和排序左右表输出字段。")

public record SpatialJoinConfiguration(
        @JsonPropertyDescription("空间谓词左侧的上游逻辑表名，必须精确引用当前输入 Map 中的一张表，并且不能与 rightTableName 相同。谓词方向以该表 Geometry 为第一个参数。")
        String leftTableName,
        @JsonPropertyDescription("空间谓词右侧的上游逻辑表名，必须精确引用另一张表。谓词方向以该表 Geometry 为第二个参数，例如 left CONTAINS right 与 left WITHIN right 含义相反。")
        String rightTableName,
        @JsonPropertyDescription("追加连接结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；左右表及其他上游表仍然保留。")
        String outputTableName,
        @JsonPropertyDescription("必填且只能为 INNER 或 LEFT。INNER 只输出满足全部空间和属性条件的左右记录组合；Canvas 4.56 起 LEFT 还保留全部左侧目标记录，未匹配时右侧投影字段为 NULL。RIGHT、FULL 明确不支持。")
        JoinType joinType,
        @JsonPropertyDescription("空间拓扑条件数组，必须包含 0 至 8 项且不能含不完整项；全部条件固定使用 AND。完全相同的左字段、谓词、右字段三元组不能重复。不配置 spatialNear 时至少需要一项拓扑条件。")
        List<SpatialJoinCondition> conditions,
        @JsonPropertyDescription("Canvas 4.55 起的可选属性等值条件。缺失/null 表示不使用属性匹配；非 null 数组最多 8 项，左右字段值使用 Spark SQL 普通等号比较，并与全部空间条件按 AND 组合。Geometry 字段不能作为属性条件。")
        List<JoinCondition> attributeConditions,
        @JsonPropertyDescription("Canvas 4.54 起的可选输出字段投影。null 保持旧版左表全部字段后接右表全部字段的行为，并在左右字段同名时拒绝；非 null 数组按顺序选择、排除或改名字段，至少一项 included=true，最终名称按大小写不敏感规则唯一。")
        List<JoinOutputColumn> outputColumns,
        @JsonPropertyDescription("Canvas 4.57 起的可选结果粒度。缺失/null 按 JOIN_ONE_TO_MANY 保持旧行为；Canvas 4.58 起可选择 JOIN_ONE_TO_ONE。")
        SpatialJoinOperation joinOperation,
        @JsonPropertyDescription("Canvas 4.58 起的一对一规则。joinOperation=JOIN_ONE_TO_ONE 时必填；其他粒度下可为空或作为非活动草稿保留。")
        SpatialJoinOneToOneOptions oneToOne,
        @JsonPropertyDescription("Canvas 4.59 起的可选时间关系；缺失/null 表示不按时间匹配。非 null 时与空间和属性条件按 AND 组合。")
        SpatialJoinTemporalCondition temporalCondition,
        @JsonPropertyDescription("Canvas 4.60 起的可选空间邻近条件；缺失/null 表示不按空间距离匹配。非 null 时与拓扑、属性和时间条件按 AND 组合。")
        SpatialJoinSpatialNearCondition spatialNear,
        @JsonPropertyDescription("Canvas 4.60 起的可选距离输出草稿。仅 enabled=true 时生效，并且只允许 JOIN_ONE_TO_MANY。")
        SpatialJoinDistanceOutput distanceOutput
) {
    public static final int MAX_CONDITIONS = 8;
    public static final int MAX_ATTRIBUTE_CONDITIONS = 8;

    public SpatialJoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
        attributeConditions = attributeConditions == null ? null : List.copyOf(attributeConditions);
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                null, null, null, null, null, null, null);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            List<JoinOutputColumn> outputColumns
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                null, outputColumns, null, null, null, null, null);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            List<JoinCondition> attributeConditions,
            List<JoinOutputColumn> outputColumns
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                attributeConditions, outputColumns, null, null, null, null, null);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            List<JoinCondition> attributeConditions,
            List<JoinOutputColumn> outputColumns,
            SpatialJoinOperation joinOperation
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                attributeConditions, outputColumns, joinOperation, null, null, null, null);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            List<JoinCondition> attributeConditions,
            List<JoinOutputColumn> outputColumns,
            SpatialJoinOperation joinOperation,
            SpatialJoinOneToOneOptions oneToOne
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                attributeConditions, outputColumns, joinOperation, oneToOne, null, null, null);
    }

    public SpatialJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<SpatialJoinCondition> conditions,
            List<JoinCondition> attributeConditions,
            List<JoinOutputColumn> outputColumns,
            SpatialJoinOperation joinOperation,
            SpatialJoinOneToOneOptions oneToOne,
            SpatialJoinTemporalCondition temporalCondition
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions,
                attributeConditions, outputColumns, joinOperation, oneToOne,
                temporalCondition, null, null);
    }

    public SpatialJoinOperation effectiveJoinOperation() {
        return joinOperation == null
                ? SpatialJoinOperation.JOIN_ONE_TO_MANY
                : joinOperation;
    }
}
