package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间密度权重：UNIFORM 在搜索半径内均匀分摊数量；KERNEL 使用随中心距离平滑衰减且积分为一的四次核。")
public enum SpatialDensityWeighting {
    UNIFORM,
    KERNEL
}
