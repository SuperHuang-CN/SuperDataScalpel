package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Geometry 简化算法。DOUGLAS_PEUCKER 优先减少顶点；显式策略下关闭隐式面积修复，结果无效会使任务失败，且不能保留 M。TOPOLOGY_PRESERVING 维护单个输入要素的拓扑约束并可保留 Z/M，但不保证多行要素之间的共享边一致。两者都是来源 CRS 坐标空间中的二维简化。")
public enum GeometrySimplifyAlgorithm {
    DOUGLAS_PEUCKER,
    TOPOLOGY_PRESERVING
}
