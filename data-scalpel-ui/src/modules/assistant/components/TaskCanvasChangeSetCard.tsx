import { ApartmentOutlined, CloseOutlined, EditOutlined, RobotOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Divider, Space, Tag, Typography } from 'antd';
import type { AssistantChangeSet } from '../model/assistant';

interface TaskCanvasChangeSetCardProps {
  changeSet: Extract<AssistantChangeSet, { changeType: 'TASK_CANVAS' }>;
  canOpen: boolean;
  rejecting: boolean;
  onOpen: () => void;
  onReject: () => void;
}

const statusLabel = {
  PENDING: '待应用',
  APPLIED: '已应用为前端草稿',
  REJECTED: '已拒绝',
  SUPERSEDED: '已被新提案替代',
  STALE: '已过期',
  FAILED: '处理失败',
} as const;

export const TaskCanvasChangeSetCard = ({
  changeSet,
  canOpen,
  rejecting,
  onOpen,
  onReject,
}: TaskCanvasChangeSetCardProps) => {
  const proposal = changeSet.taskCanvasProposal;
  const pending = changeSet.status === 'PENDING';
  const reopenable = pending || changeSet.status === 'APPLIED';
  return (
    <Card
      size="small"
      className="assistant-change-card"
      title={<Space><RobotOutlined /><span>任务 Canvas 提案</span><Tag color={pending ? 'processing' : changeSet.status === 'APPLIED' ? 'success' : 'default'}>{statusLabel[changeSet.status]}</Tag></Space>}
    >
      <Typography.Paragraph type="secondary">{proposal.summary}</Typography.Paragraph>
      <Space wrap size={[6, 6]}>
        <Tag color="blue">批任务</Tag>
        <Tag>{proposal.nodeCount} 个节点</Tag>
        <Tag>{proposal.plan.inputs.length} 个输入</Tag>
        <Tag>{proposal.plan.steps.length} 个处理</Tag>
        <Tag>{proposal.plan.output.type}</Tag>
      </Space>
      <div className="assistant-change-section">
        <Typography.Text strong><ApartmentOutlined /> 处理链路</Typography.Text>
        <div className="assistant-change-item">
          <Typography.Text>{proposal.plan.inputs.map((input) => input.name).join(' + ')}</Typography.Text>
          <Typography.Text type="secondary">
            {proposal.plan.steps.map((step) => step.name).join(' → ') || '直接输出'} → {proposal.plan.output.name}
          </Typography.Text>
        </div>
      </div>
      {proposal.assumptions.length > 0 && (
        <Alert type="info" showIcon message="方案假设" description={proposal.assumptions.join('；')} style={{ marginTop: 10 }} />
      )}
      {proposal.needsUserInput.length > 0 && (
        <Alert type="warning" showIcon message="应用后仍需补充配置" description={proposal.needsUserInput.join('；')} style={{ marginTop: 10 }} />
      )}
      {changeSet.failureSummary && (
        <Alert type="warning" showIcon message={changeSet.failureSummary} style={{ marginTop: 10 }} />
      )}
      {changeSet.taskCanvasResult && (
        <Alert type="success" showIcon message="提案已交付为前端未保存草稿；只有点击任务页面的“保存定义”才会落库。" style={{ marginTop: 10 }} />
      )}
      {reopenable && <>
        <Divider />
        <Space>
          {pending && <Button icon={<CloseOutlined />} loading={rejecting} onClick={onReject}>拒绝</Button>}
          <Button type="primary" icon={<EditOutlined />} disabled={!canOpen} onClick={onOpen}>
            {changeSet.status === 'APPLIED'
              ? '重新打开未保存提案'
              : proposal.existingTask ? '进入编辑器核对' : '创建任务并核对'}
          </Button>
        </Space>
        {!canOpen && <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
          当前账号缺少任务创建或修改权限，不能应用提案。
        </Typography.Paragraph>}
      </>}
    </Card>
  );
};
