package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("EXACT_DISTANCE 最近邻的独立连接线结果配置；连接线表复用主匹配表的同一惰性匹配关系和投影字段，只包含已匹配记录，并追加来源与候选最近位置之间的 XY MultiLineString。")
public record SpatialNearestConnectionLines(
        @JsonPropertyDescription("只有 true 才额外输出连接线表；false 或 NULL 均关闭，关闭时其余连接线字段作为草稿保留但不参与校验或执行。")
        Boolean enabled,
        @JsonPropertyDescription("启用时必填的连接线结果逻辑表名；必须与所有输入表及主匹配输出表不同。")
        String outputTableName,
        @JsonPropertyDescription("启用时必填的连接线 Geometry 输出字段名；不能与主匹配表的投影、距离或排名字段重名，输出类型为来源 CRS 下的 XY MultiLineString。")
        String geometryColumnName,
        @JsonPropertyDescription("启用 GEODESIC 连接线时必填的有限正数最大分段长度，用于加密 WGS84 测地线并处理日期线；单条连接线最多生成 100 万个顶点。PLANAR 或未启用时忽略。")
        Double maximumGeodesicSegmentLength,
        @JsonPropertyDescription("启用 GEODESIC 连接线时必填的明确线性单位，不能使用 SOURCE_CRS_UNIT；PLANAR 或未启用时忽略。")
        SpatialDistanceUnit maximumGeodesicSegmentLengthUnit
) {
    public boolean active() { return Boolean.TRUE.equals(enabled); }
}
