package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "模型相对任务的数据流角色。")

public enum ModelTaskRelationRole {
    INPUT,
    OUTPUT
}
