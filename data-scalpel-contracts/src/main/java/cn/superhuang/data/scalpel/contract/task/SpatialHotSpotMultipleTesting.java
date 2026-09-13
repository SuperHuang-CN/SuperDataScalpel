package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("热点置信分级的多重检验策略；原始 p 值始终保留。")
public enum SpatialHotSpotMultipleTesting {
    NONE,
    FDR_BH
}
