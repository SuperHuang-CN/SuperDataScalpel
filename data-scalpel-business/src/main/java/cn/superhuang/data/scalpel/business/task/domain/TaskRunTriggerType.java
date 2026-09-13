package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "运行触发来源：WORKFLOW 工作流节点；MANUAL 用户手动；SCHEDULED 运行计划。")
public enum TaskRunTriggerType {
    WORKFLOW,
    MANUAL,
    SCHEDULED
}
