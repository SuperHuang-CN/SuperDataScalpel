package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("最近邻匹配语义：EXACT_DISTANCE 用 KNN 或业务半径取得候选范围，再恢复边界同距候选并按真实距离与候选 ID 排名；LEGACY_KNN 直接采用旧 ST_KNN 候选集合，同距候选可能在最终排序前已被截断。")
public enum SpatialNearestMatchSemantics { EXACT_DISTANCE, LEGACY_KNN }
