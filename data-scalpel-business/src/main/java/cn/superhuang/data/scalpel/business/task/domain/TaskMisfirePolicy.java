package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Cron 错过触发时间的处理策略：FIRE_ONCE_NOW 立即补触发一次；SKIP 跳过错过的触发。")
public enum TaskMisfirePolicy {
    FIRE_ONCE_NOW,
    SKIP
}
