package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("真实距离最近邻的 WGS84 Geometry 范围。POINT_ONLY 保留 Canvas 4.30 的仅 Point 兼容语义；GEOMETRY 使用真实几何最近位置，支持点、多点、线、多线、面和多面，不使用质心替代。")
public enum SpatialNearestGeodesicGeometryMode {
    POINT_ONLY,
    GEOMETRY
}
