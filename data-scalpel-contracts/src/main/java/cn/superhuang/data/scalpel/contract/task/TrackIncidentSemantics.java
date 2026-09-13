package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Explicit opt-in: existing definitions retain their original incident semantics. */
@JsonClassDescription("事件状态机语义：LEGACY 在开始条件由不成立变为成立时开启事件，结束条件命中行仍属于活动事件，缺少结束条件时持续到分段末尾；CONDITION_LIFECYCLE 输出 Started/OnGoing/Ended 状态，结束行不再活动，缺少结束条件时以开始条件首次不成立作为结束。")
public enum TrackIncidentSemantics {
    LEGACY,
    CONDITION_LIFECYCLE
}
