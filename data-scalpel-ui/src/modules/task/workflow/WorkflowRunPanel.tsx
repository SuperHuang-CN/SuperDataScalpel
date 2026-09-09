import { useQuery } from '@tanstack/react-query';
import { Button, Space, Spin, Table, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { CompactAlert } from '../../../shared/components/ContextualFeedback';
import { taskRunStatusLabels, type TaskRunStatus } from '../model/task';
import { fetchWorkflowRun } from './workflowApi';
import { WorkflowGraph } from './WorkflowGraph';

const active = ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED'];
const projectedLabels: Record<string, string> = { WAITING: '等待依赖', BLOCKED: '已阻断' };
export default function WorkflowRunPanel({ runId }: { runId: string }) {
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['workflow-run', runId], queryFn: () => fetchWorkflowRun(runId),
    refetchInterval: value => value.state.data && (active.includes(value.state.data.run.status) || value.state.data.hasActiveChildren) ? 1000 : false,
  });
  if (query.isPending) return <Spin />;
  if (query.isError) return <CompactAlert type="error" message="工作流运行图加载失败" description={query.error.message}
    action={<Button onClick={() => void query.refetch()}>重试</Button>} />;
  const value = query.data;
  const openNode = (id: string) => {
    const child = value.nodes.find(node => node.id === id)?.childRun;
    if (child) navigate(`/task/${child.taskId}?tab=runs&runId=${child.id}`);
  };
  return <section className="workflow-panel">
    <Space wrap><Typography.Text strong>节点进度</Typography.Text><Tag color="success">{value.succeededNodes} / {value.totalNodes} 成功</Tag>
      {value.hasActiveChildren && !active.includes(value.run.status) && <Tag color="warning">子任务仍在停止中</Tag>}
    </Space>
    <WorkflowGraph definition={value.definition} names={Object.fromEntries(value.nodes.map(node => [node.taskId, node.taskName ?? '引用任务不可用']))}
      states={Object.fromEntries(value.nodes.map(node => [node.id, node.status]))} onSelect={selection => { if (selection.nodeId) openNode(selection.nodeId); }} onOpenNode={openNode} />
    <Table size="small" rowKey="id" pagination={false} dataSource={value.nodes} columns={[
      { title: '任务', dataIndex: 'taskName', render: (name: string | null, node) => <Button className="workflow-node-link" type="link" onClick={() => navigate(`/task/${node.taskId}`)}>{name ?? '引用任务不可用'}</Button> },
      { title: '状态', dataIndex: 'status', render: (state: string) => projectedLabels[state] ?? taskRunStatusLabels[state as TaskRunStatus] ?? state },
      { title: '执行版本', render: (_, node) => node.childRun ? `v${node.childRun.definitionVersion}` : '—' },
      { title: '结果', dataIndex: 'message', ellipsis: true, render: (text: string | null) => text ?? '—' },
      { title: '运行', width: 80, render: (_, node) => node.childRun ? <Button type="link" onClick={() => openNode(node.id)}>详情</Button> : '—' },
    ]} />
  </section>;
}
