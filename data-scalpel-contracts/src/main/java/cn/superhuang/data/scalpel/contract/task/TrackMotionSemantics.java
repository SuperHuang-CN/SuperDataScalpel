package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹运动统计语义。LEGACY_LAG 用 historyPoints 作为固定 lag 并输出 metrics；OBSERVATION_WINDOW 用 windowOptions 计算相邻观测瞬时值和最近 N 个观测的窗口统计。null 按 LEGACY_LAG。")
public enum TrackMotionSemantics { LEGACY_LAG, OBSERVATION_WINDOW }
