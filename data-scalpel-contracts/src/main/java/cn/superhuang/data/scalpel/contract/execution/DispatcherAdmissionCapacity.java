package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Effective admission limits applied by the currently registered Dispatcher. */
@JsonClassDescription("当前注册 Dispatcher 实际采用的排队、并发提交和在途执行准入上限。")
public record DispatcherAdmissionCapacity(
        @JsonPropertyDescription("允许等待提交的执行数量上限；达到上限时拒绝新执行，0 表示不允许排队。")
        int maxQueuedExecutions,
        @JsonPropertyDescription("允许同时处于 SUBMITTING 状态、正在调用外部后端提交命令的执行数量上限，最小为 1。")
        int maxConcurrentSubmissions,
        @JsonPropertyDescription("SUBMITTING、SUBMITTED、RUNNING、CANCEL_REQUESTED 执行总数上限；0 表示不设置额外上限。")
        int maxInFlightApplications
) {
}
