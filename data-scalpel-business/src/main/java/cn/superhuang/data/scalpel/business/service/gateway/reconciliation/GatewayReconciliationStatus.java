package cn.superhuang.data.scalpel.business.service.gateway.reconciliation;

import io.swagger.v3.oas.annotations.media.Schema;

/** Last explicitly requested comparison between local desired state and a gateway binding. */
@Schema(description = "最近一次显式网关对账状态：NOT_CHECKED 未检查；CHECKING 检查中；IN_SYNC 远端与本地期望一致；DRIFTED 存在差异；CHECK_FAILED 无法完成检查。对账只检查并记录，不自动修复。")
public enum GatewayReconciliationStatus {
    NOT_CHECKED,
    CHECKING,
    IN_SYNC,
    DRIFTED,
    CHECK_FAILED
}
