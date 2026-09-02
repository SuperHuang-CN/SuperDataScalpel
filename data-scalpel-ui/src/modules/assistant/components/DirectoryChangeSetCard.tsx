import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Card, Divider, Space, Tag, Typography } from 'antd';
import type { AssistantChangeSet } from '../model/assistant';

interface DirectoryChangeSetCardProps {
  changeSet: Extract<AssistantChangeSet, { changeType: 'DIRECTORY' }>;
  canManage: boolean;
  approving: boolean;
  rejecting: boolean;
  onApprove: () => void;
  onReject: () => void;
}

const statusLabels: Record<AssistantChangeSet['status'], string> = {
  PENDING: '待确认', APPLIED: '已执行', REJECTED: '已拒绝', SUPERSEDED: '已被新计划替代', STALE: '计划已过期', FAILED: '执行失败',
};

const optional = (value: string | null) => value || '—';

export const DirectoryChangeSetCard = ({ changeSet, canManage, approving, rejecting, onApprove, onReject }: DirectoryChangeSetCardProps) => {
  const plan = changeSet.directoryPlan;
  const pending = changeSet.status === 'PENDING';
  return <Card size="small" className="assistant-change-card" title={<Space><span>目录变更计划</span><Tag color={pending ? 'processing' : changeSet.status === 'APPLIED' ? 'success' : 'default'}>{statusLabels[changeSet.status]}</Tag></Space>}>
    <Typography.Paragraph type="secondary">{changeSet.summary}</Typography.Paragraph>
    <Space wrap size={[6, 6]}><Tag>{plan.scope}</Tag><Tag color="green">新增 {plan.creates.length}</Tag><Tag color="blue">修改 {plan.updates.length}</Tag><Tag color="red">删除 {plan.deletes.length}</Tag></Space>
    {plan.creates.length > 0 && <div className="assistant-change-section">
      <Typography.Text strong><PlusOutlined /> 新增</Typography.Text>
      {plan.creates.map((item) => <div className="assistant-change-item" key={item.ref}><Typography.Text>{item.targetPath}</Typography.Text><Typography.Text type="secondary">排序 {item.sortOrder} · {optional(item.description)}</Typography.Text></div>)}
    </div>}
    {plan.updates.length > 0 && <div className="assistant-change-section">
      <Typography.Text strong><EditOutlined /> 修改或移动</Typography.Text>
      {plan.updates.map((item) => <div className="assistant-change-item" key={item.id}>
        <Typography.Text>{item.current.path} → {item.targetPath}</Typography.Text>
        <Typography.Text type="secondary">名称：{item.current.name} → {item.name}；排序：{item.current.sortOrder} → {item.sortOrder}</Typography.Text>
        <Typography.Text type="secondary">说明：{optional(item.current.description)} → {optional(item.description)}；资源 {item.current.resourceCount}</Typography.Text>
      </div>)}
    </div>}
    {plan.deletes.length > 0 && <div className="assistant-change-section assistant-change-section-danger">
      <Typography.Text strong type="danger"><DeleteOutlined /> 删除空叶子目录</Typography.Text>
      {plan.deletes.map((item) => <div className="assistant-change-item" key={item.id}><Typography.Text type="danger">{item.path}</Typography.Text><Typography.Text type="secondary">直属资源 {item.directResourceCount} · 后代资源 {item.resourceCount}</Typography.Text></div>)}
    </div>}
    {changeSet.failureSummary && <Alert type="warning" showIcon message={changeSet.failureSummary} style={{ marginTop: 10 }} />}
    {changeSet.directoryResult && <Alert type="success" showIcon message={`执行完成：新增 ${changeSet.directoryResult.created.length}、修改 ${changeSet.directoryResult.updated.length}、删除 ${changeSet.directoryResult.deleted.length}`} style={{ marginTop: 10 }} />}
    {pending && <><Divider /><Space>
      <Button icon={<CloseOutlined />} loading={rejecting} onClick={onReject}>拒绝</Button>
      <Button type="primary" danger={plan.deletes.length > 0} icon={<CheckOutlined />} disabled={!canManage} loading={approving} onClick={onApprove}>{plan.deletes.length > 0 ? '确认危险变更' : '确认执行'}</Button>
    </Space>{!canManage && <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>当前账号没有目录维护权限，不能执行计划。</Typography.Paragraph>}</>}
  </Card>;
};
