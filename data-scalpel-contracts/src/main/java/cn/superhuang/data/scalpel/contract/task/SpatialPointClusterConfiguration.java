package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("有界批处理点聚类配置；对一张 XY Point 表执行 DBSCAN 或 HDBSCAN，保留参与聚类记录的全部来源字段并追加簇 ID、噪声标记及 HDBSCAN 诊断。NULL/Empty 点不参与，结果不继承 Watermark；集群运行需配置共享 spark.checkpoint.dir。MULTI_SCALE 仅能保存草稿，当前不能执行。")
public record SpatialPointClusterConfiguration(
        @JsonPropertyDescription("必填的有界上游 Canvas 逻辑表名；流式输入不受支持。")
        String sourceTableName,
        @JsonPropertyDescription("必填的 XY Point Geometry 字段名，必须带完整 CRS 元数据。NULL/Empty 点不参与聚类；非 Point、无效几何或非有限 XY 在惰性执行时失败。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("必填且不能为 Geometry 的要素身份字段名；每个实际参与点的值必须非 NULL 且唯一，相同坐标但不同 ID 仍是不同观测。真实数据约束在惰性执行时检查。")
        String featureIdColumnName,
        @JsonPropertyDescription("必填的距离方式：PLANAR 使用来源 CRS 的 XY 坐标距离；GEODESIC 只支持 EPSG:4326 XY Point，并使用 WGS84 测地距离。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("必填的多态算法参数，通过 algorithm 选择 DBSCAN、HDBSCAN 或不可执行的 MULTI_SCALE 草稿。minimumFeatures 均须为 2 至 100000，并包含观测自身。")
        SpatialPointClusterParameters parameters,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填且不能与来源字段或其他输出重名的 LONG 簇 ID 字段名；噪声点为 NULL。簇编号只标识本次运行结果，不承诺跨重跑稳定。")
        String clusterIdColumnName,
        @JsonPropertyDescription("必填且不能与来源字段或其他输出重名的 BOOLEAN 噪声字段名；未归入簇时为 true，已归入簇时为 false。")
        String noiseColumnName,
        @JsonPropertyDescription("可选的 Canvas 4.40 DBSCAN 执行语义与时间邻域；仅 parameters.algorithm=DBSCAN 时使用。NULL 保留旧 LEGACY_SPATIAL 路径；非 NULL 对象即使当前算法不是 DBSCAN 也触发版本要求。")
        SpatialDbscanOptions dbscan,
        @JsonPropertyDescription("Canvas 4.45 HDBSCAN 四个诊断输出字段；parameters.algorithm=HDBSCAN 时必填且执行，其他算法下作为非活动草稿保留。非 NULL 对象无论当前算法为何都触发版本要求。")
        SpatialHdbscanOptions hdbscan
) {
    public SpatialPointClusterConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            SpatialDistanceMethod distanceMethod, SpatialPointClusterParameters parameters, String outputTableName,
            String clusterIdColumnName, String noiseColumnName, SpatialDbscanOptions dbscan) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, distanceMethod, parameters, outputTableName,
                clusterIdColumnName, noiseColumnName, dbscan, null);
    }
    public SpatialPointClusterConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            SpatialDistanceMethod distanceMethod, SpatialPointClusterParameters parameters, String outputTableName,
            String clusterIdColumnName, String noiseColumnName) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, distanceMethod, parameters, outputTableName,
                clusterIdColumnName, noiseColumnName, null);
    }
}
