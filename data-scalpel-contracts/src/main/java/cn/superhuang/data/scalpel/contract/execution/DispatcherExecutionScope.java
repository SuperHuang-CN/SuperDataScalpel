package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Fixed execution list projections exposed by a Dispatcher runtime view. */
@JsonClassDescription("Dispatcher 执行列表范围：ACTIVE 全部未终态执行，包括排队和运行中；QUEUED 只含排队执行并提供队列位置；RECENT 最近进入终态的执行。")
public enum DispatcherExecutionScope {
    ACTIVE,
    QUEUED,
    RECENT
}
