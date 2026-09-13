package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据服务业务生命周期：DRAFT 从未成功启用；ENABLED 期望对外运行，但停用过程失败时也会暂时保持该状态；DISABLED 已成功从运行引擎移除。实际引擎部署和网关发布分别看 deploymentStatus 与 gatewayBindings，不能仅凭本字段判断可调用。")
public enum DataServiceStatus {
    DRAFT,
    ENABLED,
    DISABLED
}
