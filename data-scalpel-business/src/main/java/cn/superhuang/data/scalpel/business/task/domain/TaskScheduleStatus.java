package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "任务运行计划状态：ENABLED 参与 Cron 调度；DISABLED 不再产生新触发。")
public enum TaskScheduleStatus {
    ENABLED,
    DISABLED
}
