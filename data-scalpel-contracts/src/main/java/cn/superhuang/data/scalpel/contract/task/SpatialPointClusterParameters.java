package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "algorithm")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.Dbscan.class, name = "DBSCAN"),
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.Hdbscan.class, name = "HDBSCAN"),
        @JsonSubTypes.Type(value = SpatialPointClusterParameters.MultiScale.class, name = "MULTI_SCALE")
})
@JsonClassDescription("点聚类算法参数，通过 algorithm 判别结构：DBSCAN 使用固定空间半径；HDBSCAN 从互达距离层次提取簇，不使用固定半径或时间邻域；MULTI_SCALE 当前只允许保留草稿，编译时明确拒绝执行。")
public sealed interface SpatialPointClusterParameters permits
        SpatialPointClusterParameters.Dbscan,
        SpatialPointClusterParameters.Hdbscan,
        SpatialPointClusterParameters.MultiScale {

    int minimumFeatures();

    @JsonClassDescription("DBSCAN 参数；以包含自身的空间或时空邻域判断核心点，只在核心点间形成连通分量，边界点不能桥接两个簇。")

    record Dbscan(
            @JsonPropertyDescription("必填的有限正数空间邻域半径；两点实际距离小于或等于该值时互为候选邻居。")
            double searchDistance,
            @JsonPropertyDescription("必填的搜索半径单位；PLANAR 按来源 CRS 换算，GEODESIC 必须使用明确线性单位且不能使用 SOURCE_CRS_UNIT。")
            SpatialDistanceUnit searchDistanceUnit,
            @JsonPropertyDescription("核心点所需的最少邻居数，范围 2 至 100000，计数包含观测自身。")
            int minimumFeatures
    ) implements SpatialPointClusterParameters {
    }

    @JsonClassDescription("HDBSCAN 参数；在整个参与点集上按互达距离、最小生成树、压缩层次树和 EOM 稳定性选择簇，不使用 DBSCAN 半径、时间窗口或隐藏分组。")

    record Hdbscan(
            @JsonPropertyDescription("最少要素数，范围 2 至 100000；同时用于包含自身的核心距离密度估计和压缩层次树最小簇大小。")
            int minimumFeatures
    ) implements SpatialPointClusterParameters {
    }

    @JsonClassDescription("尚未实现的多尺度热点聚类草稿参数；当前编译始终返回 SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE，不会执行近似替代算法。")

    record MultiScale(
            @JsonPropertyDescription("草稿中的最少要素数，仍须在 2 至 100000 之间。")
            int minimumFeatures,
            @JsonPropertyDescription("草稿中的有限敏感度，允许范围 0 至 100；当前不参与任何可执行计算。")
            double sensitivity
    ) implements SpatialPointClusterParameters {
    }
}
