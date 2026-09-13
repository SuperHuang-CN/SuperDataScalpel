package cn.superhuang.data.scalpel.business.compute.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "计算引擎注册状态：CREATED 已创建未注册；REGISTERING 正在注册；ACTIVE 接受新任务；DRAINING 停止接收新任务并等待已有执行结束；INACTIVE 已正常反注册；DETACHED 因 Dispatcher 不可达而仅在 Admin 解除绑定；ERROR 注册失败或状态异常。")
public enum ComputeEngineRegistrationState {
    CREATED,
    REGISTERING,
    ACTIVE,
    DRAINING,
    INACTIVE,
    DETACHED,
    ERROR
}
