package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务引擎业务来源地址策略同步状态：NOT_CONFIGURED 未保存策略；PENDING 当前同步调用已开始但尚未确认，不代表后台排队；READY 当前期望修订已应用；FAILED 最近应用失败；OUTDATED 引擎地址或管理身份变化后需重新同步。失败不自动重试。")
public enum ServiceEngineAccessPolicyStatus {
    NOT_CONFIGURED,
    PENDING,
    READY,
    FAILED,
    OUTDATED
}
