package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("普通 gap 或表达式切分处的观测共享方式。GAP 不共享端点；FINISH_LAST 把后段首观测也放入前段；START_NEXT 把前段末观测也放入后段。固定周期边界始终按 GAP。")
public enum TrackSplitBoundaryOption { GAP, FINISH_LAST, START_NEXT }
