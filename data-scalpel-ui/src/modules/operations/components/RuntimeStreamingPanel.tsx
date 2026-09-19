import { MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Modal, Select, Table, Tag, Tooltip, message } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { searchEquals } from '../../../shared/search';
import { ManagementFilterActions } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useStopTaskRun, useForceTerminateTaskRun } from '../../task';
import { useCurrentUser } from '../../system';
import { useRuntimeStreaming } from '../hooks/useOperations';
import type { RuntimeStreaming, RuntimeStreamingQuery } from '../model/operations';
import { OperationsTable } from './OperationsTable';
import { RuntimeRunDrawer } from './RuntimeRunDrawer';
import { durationLabel } from '../model/runtimePresentation';

const labels = { STARTING: '启动中', RUNNING: '运行中', STOPPING: '停止中', STOPPED: '已停止', FAILED: '失败' };
export const RuntimeStreamingPanel = () => {
  const [form] = Form.useForm<{ state?: RuntimeStreaming['actualState'] }>(); const [state, setState] = useState<RuntimeStreaming['actualState']>();
  const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [runId, setRunId] = useState<string | null>(null);
  const query = useRuntimeStreaming({ page, size, sort: '-createdAt', search: searchEquals('actualState', state) });
  const stop = useStopTaskRun(); const terminate = useForceTerminateTaskRun(); const user = useCurrentUser(); const client = useQueryClient();
  const [modal, modalContext] = Modal.useModal(); const [notice, noticeContext] = message.useMessage();
  const command = (row: RuntimeStreaming, force: boolean) => modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay', title: force ? '强制终止实时运行' : '正常停止实时运行',
    content: `确认${force ? '强制终止' : '停止'}“${row.taskName}”当前运行吗？${force ? '当前批次可能尚未提交完成。' : ''}`,
    okText: force ? '强制终止' : '停止', cancelText: '返回', okButtonProps: { danger: true },
    onOk: async () => {
      if (!row.currentRunId) return;
      try { await (force ? terminate : stop).mutateAsync(row.currentRunId); await client.invalidateQueries({ queryKey: ['operations'] }); notice.success('操作已提交'); }
      catch (error) { notice.error(error instanceof Error ? error.message : '操作失败'); throw error; }
    },
  });
  const columns: TableProps<RuntimeStreaming>['columns'] = [
    { title: '实时任务', width: 250, render: (_, r) => <ManagementListCell primary={r.sourceExists ? <Link to={`/task/${r.taskId}?tab=streaming`}>{r.taskName}</Link> : r.taskName}
      secondary={r.currentRunId ? <Button type="link" className="ops-name-button" onClick={() => setRunId(r.currentRunId)}>运行 {r.currentRunId.slice(0, 8)}</Button> : '暂无运行'} /> },
    { title: '状态 / 持续时间', width: 170, render: (_, r) => <ManagementListCell primary={<Tag color={r.actualState === 'FAILED' ? 'error' : r.actualState === 'RUNNING' ? 'processing' : 'default'}>{labels[r.actualState]}</Tag>} secondary={r.errorSummary ? <Tooltip title={`${formatManagementDateTime(r.lastErrorAt)} · ${r.errorSummary}`}>最近运行异常</Tooltip> : durationLabel(r.startedAt, r.stoppedAt)} /> },
    { title: '引擎', dataIndex: 'engineName', width: 160, ellipsis: true, render: v => v ?? '—' },
    { title: '最近采样吞吐', width: 160, align: 'right', render: (_, r) => <ManagementListCell primary={r.progressStale || !r.queries.length || r.queries.some(q => q.inputRowsPerSecond == null) ? '—' : `${r.queries.reduce((sum, q) => sum + (q.inputRowsPerSecond ?? 0), 0).toFixed(1)} 行/秒`} secondary={r.progressStale ? '进度观测待更新' : `${r.queries.length} 个 Query`} /> },
    { title: '来源延迟', width: 180, render: (_, r) => <ManagementListCell primary={r.cursorLagMillis == null ? '—' : `${r.cursorLagMillis.toLocaleString()} ms`} secondary={r.sourceKind ?? '无来源延迟指标'} /> },
    { title: '最近进度', width: 155, dataIndex: 'lastProgressAt', render: value => <ManagementDateTime value={value} /> },
    { title: '操作', width: 70, render: (_, r) => {
      const active = ['STARTING', 'RUNNING', 'STOPPING'].includes(r.actualState) && Boolean(r.currentRunId);
      const canExecute = user.data?.permissions.includes('task.execute') ?? false;
      return <Dropdown trigger={['click']} menu={{ items: [
        ...(r.currentRunId ? [{ key: 'view', label: '查看运行', onClick: () => setRunId(r.currentRunId) }] : []),
        ...(active && canExecute ? [{ key: 'stop', label: '正常停止', disabled: r.actualState === 'STOPPING', onClick: () => command(r, false) },
          { key: 'terminate', label: '强制终止', danger: true, disabled: r.actualState !== 'STOPPING', onClick: () => command(r, true) }] : []),
      ] }}><Tooltip title="更多操作"><Button type="text" icon={<MoreOutlined />} aria-label={`操作实时任务 ${r.taskName}`} /></Tooltip></Dropdown>;
    } },
  ];
  const queryColumns: TableProps<RuntimeStreamingQuery>['columns'] = [
    { title: 'Query', dataIndex: 'name' }, { title: '状态', dataIndex: 'state' }, { title: '批次', dataIndex: 'batchId', render: v => v ?? '—', align: 'right' },
    { title: '输入行数', dataIndex: 'inputRows', render: v => v ?? '—', align: 'right' },
    { title: '输入 / 处理速率', render: (_, q) => `${q.inputRowsPerSecond ?? '—'} / ${q.processedRowsPerSecond ?? '—'} 行/秒`, align: 'right' },
    { title: '采样时间', dataIndex: 'lastProgressAt', render: formatManagementDateTime },
  ];
  return <section className="management-workbench">{modalContext}{noticeContext}
    <div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={v => { setState(v.state); setPage(0); }}>
      <Form.Item name="state"><Select allowClear placeholder="全部部署状态" style={{ width: 160 }} options={Object.entries(labels).map(([value, label]) => ({ value, label }))} /></Form.Item>
    </Form><ManagementFilterActions form={form} appliedFilters={{ state }} onReset={() => { form.resetFields(); setState(undefined); setPage(0); }} /></div>
    <OperationsTable title="当前实时部署" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }}
      extra={<ContextHelp ariaLabel="实时运行观测" content="仅展示正式部署。无输入数据和进度未更新不直接判断为故障。延迟是来源提供的指标，不能当作统一业务新鲜度。展开行可查看各 Query 的采样时间。" />}
      expandable={{ expandedRowRender: r => <Table size="small" rowKey="id" columns={queryColumns} dataSource={r.queries} pagination={false} />, rowExpandable: r => r.queries.length > 0 }} />
    <RuntimeRunDrawer runId={runId} onClose={() => setRunId(null)} />
  </section>;
};
