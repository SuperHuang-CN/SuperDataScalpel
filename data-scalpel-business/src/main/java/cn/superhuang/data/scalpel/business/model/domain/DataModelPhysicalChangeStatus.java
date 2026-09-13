package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lifecycle of a persisted physical-table change plan. */
@Schema(description = "物理变更计划状态：PLANNED 待执行，APPLYING 执行中，SUCCEEDED 成功，FAILED 已失败且未应用，PARTIAL 可能部分完成需人工核验，CANCELLED 已取消，SUPERSEDED 已被新计划替代")
public enum DataModelPhysicalChangeStatus {
    PLANNED,
    APPLYING,
    SUCCEEDED,
    FAILED,
    PARTIAL,
    CANCELLED,
    SUPERSEDED
}
