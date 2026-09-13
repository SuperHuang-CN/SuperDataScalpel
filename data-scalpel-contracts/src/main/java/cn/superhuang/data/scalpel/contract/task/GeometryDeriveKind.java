package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("几何派生函数。CENTROID 计算单个要素的质心且不保证位于凹面或洞面内部；POINT_ON_SURFACE 为有效非空面生成内部代表点；ENVELOPE 生成轴对齐包络；CONVEX_HULL 生成凸包；BOUNDARY 生成拓扑边界。ENVELOPE、CONVEX_HULL 和 BOUNDARY 可能因输入退化为点、线或 Empty，因此结果声明为通用 GEOMETRY。")
public enum GeometryDeriveKind {
    CENTROID,
    POINT_ON_SURFACE,
    ENVELOPE,
    CONVEX_HULL,
    BOUNDARY
}
