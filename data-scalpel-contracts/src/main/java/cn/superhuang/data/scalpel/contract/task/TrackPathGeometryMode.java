package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("有序线轨迹的 Geometry 构造模式。METHOD_PATH 遵循 PLANAR/GEODESIC 方法并输出 MultiLineString；LEGACY_VERTEX_LINE 直接连接原始观测顶点并输出 LineString。")
public enum TrackPathGeometryMode { METHOD_PATH, LEGACY_VERTEX_LINE }
