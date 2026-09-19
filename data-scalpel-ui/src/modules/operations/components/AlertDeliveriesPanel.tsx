import { ReloadOutlined } from '@ant-design/icons';
import { Button, Pagination, Table, Tag, Tooltip, message } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { andSearch, searchEquals } from '../../../shared/search';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { useDeliveries, useOperationsMutation } from '../hooks/useOperations';
import { retryDelivery } from '../api/operationsApi';
import { deliveryLabels, eventLabels, type AlertDelivery } from '../model/operations';
import { OperationsTable } from './OperationsTable';

export const AlertDeliveriesPanel = ({ incidentId, channelId, embedded = false }: { incidentId?: string; channelId?: string; embedded?: boolean }) => {
  const [page, setPage] = useState(0); const [size, setSize] = useState(embedded ? 10 : 20);
  const query = useDeliveries({ page, size, sort: '-createdAt', search: andSearch(searchEquals('incidentId', incidentId), searchEquals('channelId', channelId)) });
  const retry = useOperationsMutation(retryDelivery); const user = useCurrentUser(); const [notice, context] = message.useMessage();
  const columns: TableProps<AlertDelivery>['columns'] = [
    { title: '通知 / 渠道', width: embedded ? 150 : 240, render: (_, d) => <ManagementListCell primary={`${eventLabels[d.eventType]} · ${d.channelName}`} secondary={<Tooltip title={d.id}>{d.id.slice(0, 8)}{!embedded && d.incidentId && <> · <Link to={`/operations/alerts?incident=${d.incidentId}`}>关联告警</Link></>}</Tooltip>} /> },
    { title: '状态 / 尝试', width: 120, render: (_, d) => <ManagementListCell primary={<Tag color={d.status === 'FAILED' ? 'error' : d.status === 'SENT' ? 'success' : 'default'}>{deliveryLabels[d.status]}</Tag>} secondary={`尝试 ${d.attempts} 次`} /> },
    { title: '响应 / 结果', width: embedded ? 210 : 360, render: (_, d) => <ManagementListCell primary={d.httpStatus ? `HTTP ${d.httpStatus} · ${d.durationMillis ?? '—'} ms` : '—'} secondary={<Tooltip title={d.lastError}>{d.lastError ?? (d.status === 'PENDING' && d.nextAttemptAt ? `下次：${new Date(d.nextAttemptAt).toLocaleString()}` : '—')}</Tooltip>} /> },
    { title: '创建时间', dataIndex: 'createdAt', width: 150, render: v => <ManagementDateTime value={v} /> },
    { title: '操作', width: 60, render: (_, d) => user.data?.permissions.includes('alert.manage') && d.status === 'FAILED' ? <Tooltip title="重试投递"><Button size="small" type="text" icon={<ReloadOutlined />} aria-label={`重试投递 ${d.id}`} loading={retry.isPending && retry.variables === d.id}
      onClick={async () => { try { await retry.mutateAsync(d.id); notice.success('已加入重试队列'); } catch (error) { notice.error(error instanceof Error ? error.message : '重试失败'); } }} /></Tooltip> : null },
  ];
  return <>{context}{embedded ? <div>
    <div className="ops-panel-toolbar"><strong>通知投递 · 共 {query.data?.totalElements ?? '—'} 条</strong><Pagination size="small" current={page + 1} pageSize={size} total={query.data?.totalElements ?? 0} onChange={p => setPage(p - 1)} showSizeChanger={false} /></div>
    {query.error && <InlineFeedback tone="error" label="投递记录加载失败" detail={query.error.message} action={<Button type="link" onClick={() => void query.refetch()}>重试</Button>} />}
    <Table size="small" className="management-table" rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isPending} pagination={false} />
  </div> : <section className="management-workbench"><OperationsTable title="通知投递" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }} /></section>}</>;
};
