package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("按空间关系及可选时间、属性关系构造无向邻接图并求传递闭包；结果复制每个来源要素并追加分组 ID。")
public record SpatialGroupByProximityConfiguration(
        @JsonPropertyDescription("需要分组的有界 Point、Line 或 Polygon 逻辑表名。")
        String sourceTableName,
        @JsonPropertyDescription("来源表中带完整 CRS 的 XY Geometry 字段名。")
        String geometryColumnName,
        @JsonPropertyDescription("必填空间关系；关系在来源表自身的两条要素之间计算。")
        SpatialGroupByProximitySpatialRelationship spatialRelationship,
        @JsonPropertyDescription("NEAR_PLANAR/NEAR_GEODESIC 必填的有限正距离；其他关系下作为非活动草稿保留。")
        Double spatialNearDistance,
        @JsonPropertyDescription("NEAR_PLANAR/NEAR_GEODESIC 必填距离单位；其他关系下作为非活动草稿保留。")
        SpatialDistanceUnit spatialNearDistanceUnit,
        @JsonPropertyDescription("可选时间关系；非 null 时与空间、全部属性关系按 AND 组合。")
        SpatialGroupByProximityTemporalCondition temporalCondition,
        @JsonPropertyDescription("可选受控对称属性关系，最多 8 项；全部条件按 AND 组合。")
        List<SpatialGroupByProximityAttributeCondition> attributeConditions,
        @JsonPropertyDescription("追加到结果中的分组 ID 字段名；每行非空，同组值相同，但数值无顺序含义且不同运行间不保证一致。")
        String groupIdColumnName,
        @JsonPropertyDescription("新生成的 Canvas 逻辑表名；来源表及其他上游表继续保留。")
        String outputTableName
) {
    public static final int MAX_ATTRIBUTE_CONDITIONS = 8;

    public SpatialGroupByProximityConfiguration {
        attributeConditions = attributeConditions == null ? null : List.copyOf(attributeConditions);
    }
}
