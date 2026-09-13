package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理最近要素分析配置；为每条来源记录查找另一张表中最近的 1 至 100 条候选记录，输出显式投影字段、距离和可选排名。Canvas 4.30 的精确距离模式可输出独立连接线表，4.48 可使用非点 WGS84 Geometry 的真实最近位置；本节点不执行路网或交通时间分析。")

public record SpatialNearestConfiguration(
        @JsonPropertyDescription("必填的来源有界逻辑表名；必须引用上游表，且不能与 candidateTableName 相同。")
        String sourceTableName,
        @JsonPropertyDescription("必填的来源 Geometry 字段名；字段必须具有完整类型、CRS 和坐标维度元数据，并与候选 Geometry 使用相同 CRS 和维度。")
        String sourceGeometryColumnName,
        @JsonPropertyDescription("必填的候选有界逻辑表名；必须引用上游表，且不能与 sourceTableName 相同。")
        String candidateTableName,
        @JsonPropertyDescription("必填的候选 Geometry 字段名；字段必须具有完整几何元数据，并与来源 Geometry 使用相同 CRS 和坐标维度。")
        String candidateGeometryColumnName,
        @JsonPropertyDescription("必填且不能为 Geometry 的候选身份字段名，用于同距候选的稳定排序。EXACT_DISTANCE 会在惰性执行时要求每条候选值非 NULL 且唯一；LEGACY_KNN 仅假定其唯一，无法在同距且同 ID 时保证顺序。")
        String candidateIdColumnName,
        @JsonPropertyDescription("必填的距离方式：PLANAR 在来源 CRS 的 XY 平面计算最近位置；GEODESIC 使用 EPSG:4326 XY 测地距离。EXACT_DISTANCE 是否允许非点由 matching.geodesicGeometryMode 决定，允许时也不以质心替代最近位置。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("每条来源记录最多返回的候选数，必须在 1 至 100 之间；候选按实际距离升序、candidateIdColumnName 升序排名。")
        int nearestCount,
        @JsonPropertyDescription("可选的有限正数搜索半径；与 maximumDistanceUnit 必须同时填写或同时为空，候选的实际距离超过该值时不匹配。EXACT_DISTANCE 不设半径时会警告潜在的大规模中间结果。")
        Double maximumDistance,
        @JsonPropertyDescription("maximumDistance 的单位；有最大距离时必填，无最大距离时必须为 NULL。GEODESIC 不能使用 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit maximumDistanceUnit,
        @JsonPropertyDescription("是否保留未匹配的来源记录；true 时每条未匹配来源保留一行，候选侧投影、距离和排名为 NULL。NULL/Empty 来源 Geometry 不参与搜索，但可按此开关保留。")
        boolean includeUnmatched,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填且不能与其他结果字段重名的距离输出字段名；未匹配行值为 NULL。EXACT_DISTANCE 输出 DECIMAL(38,12)，按所选单位四舍五入到 12 位小数，排名仍使用未舍入距离。")
        String distanceColumnName,
        @JsonPropertyDescription("必填的距离输出单位；PLANAR 可使用可由来源 CRS 换算的单位，地理 CRS 的平面距离使用角度会产生纬度相关结果；GEODESIC 不能使用 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit distanceOutputUnit,
        @JsonPropertyDescription("可选的 INTEGER 排名输出字段名；NULL 表示不输出排名，空白字符串无效。排名从 1 开始，未匹配行值为 NULL。")
        String rankColumnName,
        @JsonPropertyDescription("必填的来源/候选字段投影，至少启用一项；同一来源字段只能配置一次，启用项输出名必须唯一且不能与距离、排名重名。includeUnmatched=true 时候选侧字段可为 NULL。")
        List<JoinOutputColumn> outputColumns,
        @JsonPropertyDescription("可选的 Canvas 4.30 匹配配置；对象缺失或为 NULL 时使用 LEGACY_KNN。对象存在且 semantics 为 NULL 时使用 EXACT_DISTANCE；显式 LEGACY_KNN 会忽略 sourceIdColumnName 和 connectionLines 的执行设置。")
        SpatialNearestMatching matching
) {
    public static final int MAX_NEAREST_COUNT = 100;

    public SpatialNearestConfiguration {
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public SpatialNearestConfiguration(String sourceTableName, String sourceGeometryColumnName, String candidateTableName,
            String candidateGeometryColumnName, String candidateIdColumnName, SpatialDistanceMethod distanceMethod,
            int nearestCount, Double maximumDistance, SpatialDistanceUnit maximumDistanceUnit, boolean includeUnmatched,
            String outputTableName, String distanceColumnName, SpatialDistanceUnit distanceOutputUnit, String rankColumnName,
            List<JoinOutputColumn> outputColumns) {
        this(sourceTableName, sourceGeometryColumnName, candidateTableName, candidateGeometryColumnName, candidateIdColumnName,
                distanceMethod, nearestCount, maximumDistance, maximumDistanceUnit, includeUnmatched, outputTableName,
                distanceColumnName, distanceOutputUnit, rankColumnName, outputColumns, null);
    }

    public boolean usesExactMatching() { return matching != null && matching.semantics() != SpatialNearestMatchSemantics.LEGACY_KNN; }
    public boolean outputsConnectionLines() { return usesExactMatching() && matching.connectionLines() != null && matching.connectionLines().active(); }
}
