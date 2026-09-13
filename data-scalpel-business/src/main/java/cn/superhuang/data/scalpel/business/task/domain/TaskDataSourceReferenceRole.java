package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "数据源相对任务的数据流角色，例如输入或输出。")
public enum TaskDataSourceReferenceRole {
    INPUT,
    OUTPUT
}
