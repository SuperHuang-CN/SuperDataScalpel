export type GatewayReconciliationStatus =
  | 'NOT_CHECKED'
  | 'CHECKING'
  | 'IN_SYNC'
  | 'DRIFTED'
  | 'CHECK_FAILED';

export type GatewayReconciliationReason =
  | 'REMOTE_MISSING'
  | 'UNEXPECTED_REMOTE'
  | 'CONFIG_MISMATCH'
  | 'OWNER_MISMATCH'
  | 'LOCAL_BINDING_MISSING'
  | 'SECRET_MISMATCH';

export interface GatewayReconciliationState {
  reconciliationStatus: GatewayReconciliationStatus;
  reconciliationReason: GatewayReconciliationReason | null;
  reconciliationMessage: string | null;
  reconciliationOperationId: string | null;
  reconciliationStartedAt: string | null;
  lastReconciledAt: string | null;
}

export const gatewayReconciliationStatusLabels: Record<GatewayReconciliationStatus, string> = {
  NOT_CHECKED: '待对账',
  CHECKING: '对账中',
  IN_SYNC: '一致',
  DRIFTED: '有漂移',
  CHECK_FAILED: '检查失败',
};

export const gatewayReconciliationReasonLabels: Record<GatewayReconciliationReason, string> = {
  REMOTE_MISSING: '网关对象丢失',
  UNEXPECTED_REMOTE: '网关对象未按期望移除',
  CONFIG_MISMATCH: '配置不一致',
  OWNER_MISMATCH: '归属不一致',
  LOCAL_BINDING_MISSING: '本地依赖绑定缺失',
  SECRET_MISMATCH: '密钥不一致',
};
