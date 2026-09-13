package cn.superhuang.data.scalpel.business.service.gateway.reconciliation;

import io.swagger.v3.oas.annotations.media.Schema;

/** Provider-neutral reason for a gateway binding drift. */
@Schema(description = "网关漂移原因：REMOTE_MISSING 期望对象缺失；UNEXPECTED_REMOTE 出现非期望对象；CONFIG_MISMATCH 配置不同；OWNER_MISMATCH 远端归属不同；LOCAL_BINDING_MISSING 本地绑定缺失；SECRET_MISMATCH 凭据秘密不一致。")
public enum GatewayReconciliationReason {
    REMOTE_MISSING,
    UNEXPECTED_REMOTE,
    CONFIG_MISMATCH,
    OWNER_MISMATCH,
    LOCAL_BINDING_MISSING,
    SECRET_MISMATCH
}
