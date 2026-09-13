package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "任务生命周期状态：DRAFT 草稿不可正式运行，但部分 Canvas/JAR 支持受限试运行；PUBLISHED 已发布，可按任务类型运行、启动或调度；DISABLED 已停用，不接受新正式运行但允许修改定义并可保留历史与已有活动运行。")
public enum TaskStatus {
    DRAFT,
    PUBLISHED,
    DISABLED
}
