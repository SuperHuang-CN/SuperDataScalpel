package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "任务运行私有 JAR 副本的清理状态：PENDING 表示终态后仍待后台删除；COMPLETED 表示已删除。删除失败时保持 PENDING 等待后续重试，没有独立 FAILED 状态。")
public enum TaskRunJarCleanupStatus {
    PENDING,
    COMPLETED
}
