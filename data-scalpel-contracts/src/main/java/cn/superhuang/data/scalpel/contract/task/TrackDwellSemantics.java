package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("驻留识别语义。LEGACY_ADJACENT 把相邻距离不超过阈值的连续观测分组后按最短时长筛选；REFERENCE_CENTER 从首点候选生成冻结种子中心并扩展未归属连续观测，且同一观测不复用于后续驻留。null 按 LEGACY_ADJACENT。")
public enum TrackDwellSemantics { LEGACY_ADJACENT, REFERENCE_CENTER }
