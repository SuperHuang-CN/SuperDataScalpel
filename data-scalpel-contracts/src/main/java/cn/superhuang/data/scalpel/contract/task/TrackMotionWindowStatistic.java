package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("轨迹运动窗口中的一个固定统计项。一个组被选择后必须配置该组全部 kind；瞬时项描述当前观测与前一观测的段，MIN/MAX/AVG/TOT 项描述完全落入最近 N 个观测窗口的测量。")
public record TrackMotionWindowStatistic(
        @JsonPropertyDescription("当前统计项的稳定 ID。")
        String statisticId,
        @JsonPropertyDescription("统计 kind；具体数值定义、窗口范围和单位归属见 TrackMotionStatistic。")
        TrackMotionStatistic kind,
        @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
        String outputColumnName
) { }
