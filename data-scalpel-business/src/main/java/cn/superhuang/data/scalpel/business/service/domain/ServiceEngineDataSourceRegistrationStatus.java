package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Admin control-plane state of one data source synchronized to one Engine. */
@Schema(description = "数据源配置同步到服务引擎的状态：PENDING 同步调用已开始但尚未确认，不代表后台排队；READY 最近一次同步成功；OUTDATED 曾成功同步但当前配置已变化或后续同步失败；FAILED 从未成功同步且最近同步失败。同步远端失败通过状态返回，不自动重试。")
public enum ServiceEngineDataSourceRegistrationStatus {
    PENDING,
    READY,
    OUTDATED,
    FAILED
}
