package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Current execution counts derived from the Dispatcher execution store. */
@JsonClassDescription("Dispatcher 执行账本按状态统计的当前准入占用；用于与准入上限一起判断是否还能接收执行。")
public record DispatcherAdmissionUsage(
        @JsonPropertyDescription("Dispatcher 账本中处于 QUEUED 状态、尚未开始提交的执行数量。")
        long queued,
        @JsonPropertyDescription("正在调用 Docker、YARN 或 Kubernetes 提交应用的执行数量。")
        long submitting,
        @JsonPropertyDescription("外部后端已接受、但 Runner 尚未确认开始运行的执行数量。")
        long submitted,
        @JsonPropertyDescription("Dispatcher 账本中处于 RUNNING 状态的执行数量。")
        long running,
        @JsonPropertyDescription("已经请求取消但尚未收敛到终态的执行数量。")
        long cancelRequested,
        @JsonPropertyDescription("所有在途执行数量，即 submitting、submitted、running 与 cancelRequested 之和；不包含 queued。")
        long inFlight
) {
}
