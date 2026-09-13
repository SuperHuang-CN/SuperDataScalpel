package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "调度重叠策略：FORBID 在前次运行未结束时跳过；ALLOW 允许同时运行。")
public enum TaskOverlapPolicy {
    FORBID,
    ALLOW
}
