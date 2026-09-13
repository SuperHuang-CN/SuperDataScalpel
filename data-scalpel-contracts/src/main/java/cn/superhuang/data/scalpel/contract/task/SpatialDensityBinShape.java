package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间密度输出格网形状；方格大小为边长，平顶六边形大小为两条平行边之间的距离。")
public enum SpatialDensityBinShape {
    SQUARE,
    HEXAGON
}
