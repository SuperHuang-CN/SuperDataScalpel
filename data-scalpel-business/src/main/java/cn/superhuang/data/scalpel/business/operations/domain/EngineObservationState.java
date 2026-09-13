package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "运维采集器对计算引擎的最近有效连通性判断：REACHABLE 已建立有效通信；UNREACHABLE 明确探测失败；UNKNOWN 从未有效观测、观测已过期或引擎未激活。依赖是否就绪由 dependenciesReady 单独表达。")
public enum EngineObservationState {
    UNKNOWN, REACHABLE, UNREACHABLE
}
