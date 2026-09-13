package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理范围内汇总配置。将被汇总要素按真实 Geometry 与 Polygon/MultiPolygon 区域或生成的平面格网相交，按区域、可选时间窗和可选分类计算 1 至 32 项统计。保留输入 Map 并追加主结果，关联分组模式还追加组表；不隐式转换 CRS，也不承诺与 ArcGIS Summarize Within 完全等价。")

public record SpatialSummarizeWithinConfiguration(
        @JsonPropertyDescription("AREA_TABLE 模式必填的区域上游逻辑表名，必须与 summaryTableName 不同且为 BOUNDED；PLANAR_GRID 模式保留但不校验或读取该草稿值。")
        String areaTableName,
        @JsonPropertyDescription("AREA_TABLE 模式必填的区域 Geometry 字段名，只接受 EPSG + XY 的 POLYGON 或 MULTIPOLYGON，并须与被汇总 Geometry 的 CRS、维度完全一致；PLANAR_GRID 模式忽略。")
        String areaGeometryColumnName,
        @JsonPropertyDescription("必填的被汇总要素上游逻辑表名，必须引用 BOUNDED 表；AREA_TABLE 模式下不能与 areaTableName 相同。")
        String summaryTableName,
        @JsonPropertyDescription("被汇总要素的 Geometry 字段名，必须具有 EPSG CRS 和 XY 维度。AREA_TABLE 模式下须与区域 Geometry 的 CRS、维度一致；格网模式还要求明确的 Point/MultiPoint、Line/MultiLine 或 Polygon/MultiPolygon 类型。")
        String summaryGeometryColumnName,
        @JsonPropertyDescription("是否保留没有相交要素的区域。true 时主结果为空区域补行，COUNT/COUNT_FIELD 为 0，其他无测量值统计通常为 null；启用时间切片时只为被汇总数据中实际出现的窗口补行，不凭空生成时间范围。关联组表不为此制造 null 分组行。")
        boolean includeEmptyAreas,
        @JsonPropertyDescription("必填空间测量方法，统一用于 LENGTH_WITHIN、AREA_WITHIN、总量分摊、交叠权重和关联组形状比例。PLANAR 在当前 CRS 坐标空间计算；GEODESIC 仅接受两侧 EPSG:4326 XY。节点不自动投影。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("必填长度输出单位，仅改变 LENGTH_WITHIN 最终显示值，不改变无量纲交叠比例。GEODESIC 不允许 SOURCE_CRS_UNIT；PLANAR 按来源 CRS 轴单位换算，地理 CRS 只允许来源角度单位。")
        SpatialDistanceUnit lengthUnit,
        @JsonPropertyDescription("必填面积输出单位，仅改变 AREA_WITHIN 最终显示值，不改变无量纲交叠比例。GEODESIC 从平方米换算；PLANAR 投影 CRS 从坐标轴平方单位换算，地理 CRS 的角度平方不能换算为这些面积单位。")
        SpatialAreaUnit areaUnit,
        @JsonPropertyDescription("AREA_TABLE 模式的区域属性投影，必须至少启用一项且所有项 sourceSide=LEFT；数组顺序决定主结果字段顺序，可改输出名。PLANAR_GRID 模式忽略此列表并固定输出 regions 配置的格网 ID 和 Geometry。")
        List<JoinOutputColumn> areaOutputColumns,
        @JsonPropertyDescription("必填有序统计项数组，包含 1 至 32 项且不能含 null；输出按配置顺序排列。各项可统计命中数、字段非空数、任意字符串、标量值、相交长度或相交面积，并可显式选择总量分摊或交叠比例加权。")
        List<SpatialWithinStatistic> statistics,
        @JsonPropertyDescription("可选分类分组配置。null 表示每区域/窗口一条总体结果；存在时按被汇总表字段分组。与 groupResult 的活动模式共同决定输出旧扁平分组行，或主表总体统计加关联组表。")
        SpatialGroupSummary groupSummary,
        @JsonPropertyDescription("可选时间切片，按被汇总表 TIMESTAMP 字段生成左闭右开窗口；每个窗口独立统计。NULL 时间、固定窗口间隙中的时间不参与；重叠窗口会复制观测。空区域只补数据中实际出现的窗口。")
        SpatialTemporalSlicing temporalSlicing,
        @JsonPropertyDescription("主结果逻辑表名，必须与所有输入表以及活动关联组表名不同。无分组时粒度为区域/格网加可选窗口；关联模式时主表仍为总体统计，不由各组统计再次平均。")
        String outputTableName,
        @JsonPropertyDescription("可选关联分组结果配置。仅当 groupSummary 存在且 mode 不是 LEGACY_FLAT 时生效；此时 outputTableName 是主表，groupResult.outputTableName 是按区域键、可选窗口和组值关联的第二张表。对象存在但未激活时仍作为草稿保存。")
        SpatialWithinGroupResult groupResult,
        @JsonPropertyDescription("可选区域来源配置。null 或 AREA_TABLE 使用 areaTableName；PLANAR_GRID 根据被汇总 Geometry 的投影 CRS、范围、原点、形状和大小生成方格/六边形区域，不把内部区域表向下游暴露。")
        SpatialWithinRegions regions
) {
    public static final int MAX_STATISTICS = 32;

    public SpatialSummarizeWithinConfiguration(String areaTableName, String areaGeometryColumnName,
            String summaryTableName, String summaryGeometryColumnName, boolean includeEmptyAreas,
            SpatialDistanceMethod distanceMethod, SpatialDistanceUnit lengthUnit, SpatialAreaUnit areaUnit,
            List<JoinOutputColumn> areaOutputColumns, List<SpatialWithinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            SpatialWithinGroupResult groupResult) {
        this(areaTableName, areaGeometryColumnName, summaryTableName, summaryGeometryColumnName, includeEmptyAreas,
                distanceMethod, lengthUnit, areaUnit, areaOutputColumns, statistics, groupSummary, temporalSlicing,
                outputTableName, groupResult, null);
    }

    public boolean usesGridRegions() { return regions != null && regions.usesGrid(); }

    public SpatialSummarizeWithinConfiguration(String areaTableName, String areaGeometryColumnName,
            String summaryTableName, String summaryGeometryColumnName, boolean includeEmptyAreas,
            SpatialDistanceMethod distanceMethod, SpatialDistanceUnit lengthUnit, SpatialAreaUnit areaUnit,
            List<JoinOutputColumn> areaOutputColumns, List<SpatialWithinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName) {
        this(areaTableName, areaGeometryColumnName, summaryTableName, summaryGeometryColumnName,
                includeEmptyAreas, distanceMethod, lengthUnit, areaUnit, areaOutputColumns, statistics,
                groupSummary, temporalSlicing, outputTableName, null);
    }

    public boolean usesLinkedGroupResult() {
        return groupSummary != null && groupResult != null && groupResult.mode() != SpatialWithinGroupResultMode.LEGACY_FLAT;
    }

    public SpatialSummarizeWithinConfiguration {
        areaOutputColumns = areaOutputColumns == null ? null : List.copyOf(areaOutputColumns);
        statistics = statistics == null ? null : List.copyOf(statistics);
    }

    public boolean usesExplicitStatistics() {
        return statistics != null && statistics.stream().filter(java.util.Objects::nonNull)
                .anyMatch(SpatialWithinStatistic::requiresExplicitStatisticsVersion);
    }
}
