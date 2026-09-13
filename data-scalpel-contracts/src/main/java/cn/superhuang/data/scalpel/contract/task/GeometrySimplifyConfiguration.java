package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("逐行 Geometry 简化配置，支持批处理和流处理。保留来源字段并追加一个通用 GEOMETRY 结果字段；容差在来源 CRS 的二维坐标空间中执行，不自动投影、修复或按地图比例尺换算。NULL 输入返回 NULL，输出表继承来源有界性、事件时间和 Watermark。")
public record GeometrySimplifyConfiguration(
        @JsonPropertyDescription("要处理的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游表继续传播但不参与简化。")
        String sourceTableName,
        @JsonPropertyDescription("要简化的来源 Geometry 字段名，必须具有受支持的 EPSG CRS 和坐标维度；原字段在结果表中保持不变。")
        String geometryColumnName,
        @JsonPropertyDescription("追加简化结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；不会替换 sourceTableName。")
        String outputTableName,
        @JsonPropertyDescription("追加的简化 Geometry 字段名，按大小写不敏感规则不能与任何来源字段重名；输出 GeometryKind 声明为 GEOMETRY。")
        String outputColumnName,
        @JsonPropertyDescription("必填简化算法。DOUGLAS_PEUCKER 按容差减少顶点，显式策略下不自动修复无效结果；TOPOLOGY_PRESERVING 保持单个要素的拓扑约束，但不保证不同行之间的共边一致。")
        GeometrySimplifyAlgorithm algorithm,
        @JsonPropertyDescription("必填容差，必须是有限正数；先按 toleranceUnit 换算到来源 CRS 轴单位，再作为二维简化距离。它不是输出精度、地图比例尺或测地距离。")
        Double tolerance,
        @JsonPropertyDescription("tolerance 的单位。投影 CRS 可使用 SOURCE_CRS_UNIT 或受支持线性单位并换算到坐标轴单位；地理 CRS 只允许 SOURCE_CRS_UNIT，其值为来源角度单位并产生警告，不自动换算为米。")
        SpatialDistanceUnit toleranceUnit,
        @JsonPropertyDescription("结果维度和有效性策略。PRESERVE_DIMENSION 校验输入并保留可支持维度，Douglas-Peucker 不支持保留 M；OUTPUT_XY 校验输入并只将新结果降为 XY；LEGACY 或 null 使用旧 Sedona 兼容路径。")
        GeometryUnaryPolicy geometryPolicy
) {
    public GeometrySimplifyConfiguration(String sourceTableName, String geometryColumnName, String outputTableName,
            String outputColumnName, GeometrySimplifyAlgorithm algorithm, double tolerance, SpatialDistanceUnit toleranceUnit) {
        this(sourceTableName, geometryColumnName, outputTableName, outputColumnName, algorithm, tolerance, toleranceUnit, null);
    }
}
