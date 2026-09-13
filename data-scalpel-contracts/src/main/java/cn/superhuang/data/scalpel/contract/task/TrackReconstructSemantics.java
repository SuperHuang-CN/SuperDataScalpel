package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹重建执行语义。ORDERED_SEGMENTS 按时间和显式次序构造确定性片段并支持新路径/面配置；LEGACY_POINTS 保留旧版 Point→LineString 行为。配置对象存在但值为 null 时按 ORDERED_SEGMENTS，整个 reconstruction 缺失时走旧路径。")
public enum TrackReconstructSemantics { ORDERED_SEGMENTS, LEGACY_POINTS }
