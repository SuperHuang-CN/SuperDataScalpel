package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;

import java.util.List;

@Schema(description = "SPARK_JAR 或 SPARK_STREAMING_JAR 用户作业通过公开 SDK 最近一次上报并由控制面持久化的业务状态与指标快照。它不替代平台 TaskRun 状态，不是指标历史或时间序列。")

public record UserJobObservabilityResponse(
        @Schema(description = "用户作业自行定义的阶段、说明和更新时间；快照只包含指标而未设置业务状态时为空。")
        UserJobStatus status,
        @Schema(description = "用户作业按指标名上报的最新值，响应中始终为数组；未注册指标时为空数组。COUNTER 是累计值，GAUGE 是当前值，TIMER 是次数和耗时汇总。")
        List<UserJobMetricSnapshot> metrics
) {
    public UserJobObservabilityResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
