package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "运行状态：QUEUED 排队；RUNNING 运行中；CANCEL_REQUESTED 已请求取消；STOP_REQUESTED 已请求正常停止；STOPPED 已停止；SUCCESS 成功；FAILED 失败；TIMED_OUT 超时；CANCELLED 已取消；SKIPPED 未执行。")
public enum TaskRunStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    STOP_REQUESTED,
    STOPPED,
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    SKIPPED
}
