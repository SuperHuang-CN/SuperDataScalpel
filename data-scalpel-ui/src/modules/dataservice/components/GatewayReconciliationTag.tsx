import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { Tag, Tooltip } from 'antd';
import {
  gatewayReconciliationReasonLabels,
  gatewayReconciliationStatusLabels,
  type GatewayReconciliationState,
  type GatewayReconciliationStatus,
} from '../model/gatewayReconciliation';

const colors: Record<GatewayReconciliationStatus, string> = {
  NOT_CHECKED: 'default',
  CHECKING: 'processing',
  IN_SYNC: 'success',
  DRIFTED: 'error',
  CHECK_FAILED: 'warning',
};

const formatDateTime = (value: string | null) => value ? formatManagementDateTime(value) : null;

export const GatewayReconciliationTag = ({
  state,
}: {
  state: GatewayReconciliationState;
}) => {
  const detail = [
    state.reconciliationReason
      ? gatewayReconciliationReasonLabels[state.reconciliationReason]
      : null,
    state.reconciliationMessage,
    state.lastReconciledAt ? `检查时间：${formatDateTime(state.lastReconciledAt)}` : null,
  ].filter(Boolean).join('；');
  return (
    <Tooltip title={detail || '尚未执行手动网关状态对账'}>
      <Tag color={colors[state.reconciliationStatus]}>
        对账 · {gatewayReconciliationStatusLabels[state.reconciliationStatus]}
      </Tag>
    </Tooltip>
  );
};
