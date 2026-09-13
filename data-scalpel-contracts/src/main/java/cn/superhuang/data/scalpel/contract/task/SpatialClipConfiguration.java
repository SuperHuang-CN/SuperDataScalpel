package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("有界批处理空间裁剪配置。使用 Polygon/MultiPolygon Mask 裁剪来源 Geometry，只保留非 NULL、非 Empty 的交集。输出保留来源属性且不输出 Mask 属性。Canvas 4.51 可显式保持来源点/线/面家族；Canvas 4.77 可将多条相交 Mask 合并后对每条来源只裁剪一次。")
public record SpatialClipConfiguration(
        @JsonPropertyDescription("被裁剪的来源逻辑表名，必须精确引用当前上游 Map 中的一张 BOUNDED 表，并且不能与 maskTableName 相同。未命中 Mask、Geometry 为 NULL 或 Empty 的来源行不会输出。")
        String sourceTableName,
        @JsonPropertyDescription("提供裁剪范围的 Mask 逻辑表名，必须精确引用另一张 BOUNDED 上游表。Mask 的属性不会进入结果；多条 Mask 是否先合并由 maskCombination 决定。")
        String maskTableName,
        @JsonPropertyDescription("追加裁剪结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；输入 Map 中的来源表、Mask 表和其他表仍然保留。")
        String outputTableName,
        @JsonPropertyDescription("来源表的 Geometry 字段名；旧版策略可使用当前支持的任意 GeometryKind，来源家族策略只接受明确点/线/面类型。字段必须具有 EPSG CRS 和 XY 维度，并与 Mask 字段的 CRS、维度完全一致；原字段会保留。")
        String sourceGeometryColumnName,
        @JsonPropertyDescription("Mask 表中的 Geometry 字段名，只接受 POLYGON 或 MULTIPOLYGON；必须具有 EPSG CRS 和 XY 维度，并与来源字段的 CRS、维度完全一致。节点不隐式转换 CRS。")
        String maskGeometryColumnName,
        @JsonPropertyDescription("追加到来源字段末尾的裁剪结果字段名，不能与来源字段重名。结果非 nullable；具体 GeometryKind 由 geometryPolicy 决定。")
        String outputColumnName,
        @JsonPropertyDescription("Canvas 4.51 起可选结果策略。SOURCE_FAMILY_2D 只接受明确点/线/面来源并输出对应 Multi 家族，过滤低维接触；LEGACY_ANY_DIMENSION 输出通用 Geometry。缺失或 null 保持旧版任意维度结果。")
        SpatialClipGeometryPolicy geometryPolicy,
        @JsonPropertyDescription("Canvas 4.77 起可选多 Mask 组合方式。DISSOLVE_ALL 对每条来源要素先合并全部相交 Mask，再执行一次 Intersection，因此重叠 Mask 不会重复输出覆盖区域，分离 Mask 片段作为同一 Multi 结果返回；PAIRWISE 每条 Mask 独立输出。缺失或 null 保持旧版 PAIRWISE。")
        SpatialClipMaskCombination maskCombination
) {
    public SpatialClipConfiguration(
            String sourceTableName,
            String maskTableName,
            String outputTableName,
            String sourceGeometryColumnName,
            String maskGeometryColumnName,
            String outputColumnName
    ) {
        this(sourceTableName, maskTableName, outputTableName, sourceGeometryColumnName,
                maskGeometryColumnName, outputColumnName, null, null);
    }

    public SpatialClipConfiguration(
            String sourceTableName,
            String maskTableName,
            String outputTableName,
            String sourceGeometryColumnName,
            String maskGeometryColumnName,
            String outputColumnName,
            SpatialClipGeometryPolicy geometryPolicy
    ) {
        this(sourceTableName, maskTableName, outputTableName, sourceGeometryColumnName,
                maskGeometryColumnName, outputColumnName, geometryPolicy, null);
    }

    public boolean usesSourceFamily() {
        return geometryPolicy == SpatialClipGeometryPolicy.SOURCE_FAMILY_2D;
    }

    public boolean dissolvesMasks() {
        return maskCombination == SpatialClipMaskCombination.DISSOLVE_ALL;
    }
}
