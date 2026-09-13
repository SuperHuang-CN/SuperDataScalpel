package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Stable lifecycle state of one execution as observed by the Dispatcher. */
@JsonClassDescription("Dispatcher 观测的外部执行状态：QUEUED 待提交；SUBMITTING 正在提交；SUBMITTED 已交给后端；RUNNING 运行中；CANCEL_REQUESTED 已请求取消；SUCCESS 成功；FAILED 失败；TIMED_OUT 超时；CANCELLED 已取消；STOPPED 正常停止；LOST 后端执行失联。")
public enum DispatcherExecutionState {
    QUEUED,
    SUBMITTING,
    SUBMITTED,
    RUNNING,
    CANCEL_REQUESTED,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    STOPPED,
    LOST;

    public boolean terminal() {
        return this == SUCCESS || this == FAILED || this == TIMED_OUT || this == CANCELLED
                || this == STOPPED || this == LOST;
    }

    public boolean inFlight() {
        return this == SUBMITTING || this == SUBMITTED || this == RUNNING || this == CANCEL_REQUESTED;
    }
}
