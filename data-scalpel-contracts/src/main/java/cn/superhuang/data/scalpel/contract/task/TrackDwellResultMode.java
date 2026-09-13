package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("驻留结果粒度。MEAN_CENTERS 每个驻留输出重新计算的均值 Point；CONVEX_HULLS 每个驻留输出可能退化为点/线的凸包；DWELL_FEATURES 只保留驻留成员原行；ALL_FEATURES 保留全部非空时间原行。点级结果追加 dwell ID 和标记，非成员 ID 为 NULL、标记为 false。")
public enum TrackDwellResultMode { MEAN_CENTERS, CONVEX_HULLS, DWELL_FEATURES, ALL_FEATURES }
