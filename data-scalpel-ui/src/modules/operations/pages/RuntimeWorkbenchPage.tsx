import { Button, Form, Input, Space, Tabs, Tooltip } from 'antd';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useCurrentUser } from '../../system';
import { taskTypeLabels } from '../../task';
import type { RuntimeRunFilters } from '../model/operations';
import { useRuntimeOverview, useAlerts } from '../hooks/useOperations';
import { RuntimeOverviewCards } from '../components/RuntimeOverviewCards';
import { RuntimeRunsPanel } from '../components/RuntimeRunsPanel';
import { RuntimeStreamingPanel } from '../components/RuntimeStreamingPanel';
import { RuntimeEnginesPanel } from '../components/RuntimeEnginesPanel';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { ruleLabels, statusLabels } from '../model/operations';
import { searchEquals } from '../../../shared/search';
import '../components/operations.css';

const RuntimeOverviewPanel = () => {
  const [range, setRange] = useState<{ from?: string; to?: string }>({}); const [form] = Form.useForm<{ from?: string; to?: string }>();
  const query = useRuntimeOverview(range); const alerts = useAlerts({ page: 0, size: 5, sort: '-occurredAt', search: searchEquals('status', 'OPEN') });
  const [params, setParams] = useSearchParams();
  const data = query.data;
  const byHour = new Map<string, { success: number; failed: number; other: number; to: string }>();
  data?.tasks?.trend.forEach(point => {
    const bucket = byHour.get(point.from) ?? { success: 0, failed: 0, other: 0, to: point.to };
    if (point.status === 'SUCCESS') bucket.success += point.count;
    else if (point.status === 'FAILED' || point.status === 'TIMED_OUT') bucket.failed += point.count;
    else bucket.other += point.count;
    byHour.set(point.from, bucket);
  });
  const maximum = Math.max(1, ...[...byHour.values()].map(v => v.success + v.failed + v.other));
  return <div className="ops-overview">
    <Form form={form} layout="inline" autoComplete="off" className="ops-inline-form" onFinish={values => setRange({ from: values.from ? new Date(values.from).toISOString() : undefined, to: values.to ? new Date(values.to).toISOString() : undefined })}>
      <Space wrap><Form.Item name="from"><Input type="datetime-local" aria-label="概览开始时间" /></Form.Item><Form.Item name="to"><Input type="datetime-local" aria-label="概览结束时间" /></Form.Item>
        <Button type="primary" htmlType="submit">查询</Button><Button type="text" onClick={() => { form.resetFields(); setRange({}); }}>最近 24 小时</Button>
      </Space>
    </Form>
    <RuntimeOverviewCards range={range} />
    {data?.tasks && <section className="ops-panel">
      <div className="ops-panel-toolbar"><strong>批任务完成趋势</strong><span>{formatManagementDateTime(data.from)} — {formatManagementDateTime(data.to)}</span></div>
      {byHour.size ? <div className="ops-trend" aria-label="按结束时间统计的批任务完成趋势">{[...byHour.entries()].map(([hour, value]) => <Tooltip key={hour} title={`${formatManagementDateTime(hour)}：成功 ${value.success}，失败 ${value.failed}，其他 ${value.other}`}>
        <button type="button" className="ops-trend-bar" aria-label={`查看 ${formatManagementDateTime(hour)} 完成的运行`}
          onClick={() => { const next = new URLSearchParams(params); next.set('tab', 'runs'); next.set('timeField', 'endedAt'); next.set('from', hour); next.set('to', value.to); next.delete('active'); next.delete('status'); next.delete('quality'); next.delete('taskType'); next.set('batch', 'true'); setParams(next); }}>
          <span className="ops-trend-other" style={{ height: `${value.other / maximum * 100}%` }} />
          <span className="ops-trend-failed" style={{ height: `${value.failed / maximum * 100}%` }} />
          <span className="ops-trend-success" style={{ height: `${value.success / maximum * 100}%` }} />
        </button></Tooltip>)}</div> : <span>所选时间范围内没有已完成的批任务</span>}
      <div className="ops-trend-legend"><span><i className="ops-trend-success" />成功</span><span><i className="ops-trend-failed" />失败 / 超时</span><span><i className="ops-trend-other" />取消 / 跳过</span></div>
    </section>}
    <section className="ops-panel"><div className="ops-panel-toolbar"><strong>需要处理的告警</strong><Link to="/operations/alerts">查看全部</Link></div>
      {alerts.error && <InlineFeedback tone="error" label="告警摘要加载失败" detail={alerts.error.message} action={<Button type="link" onClick={() => void alerts.refetch()}>重试</Button>} />}
      {alerts.data?.content.map(i => <div className="ops-notification-row" key={i.id}><Link to={`/operations/alerts?incident=${i.id}`}>{i.subjectName} · {ruleLabels[i.ruleType]}</Link><span>{statusLabels[i.status]} · {formatManagementDateTime(i.occurredAt)}</span></div>)}
      {alerts.data?.content.length === 0 && <span>当前没有待确认告警</span>}
    </section>
  </div>;
};
const statuses: NonNullable<RuntimeRunFilters['status']>[] = ['FAILED_OR_TIMED_OUT', 'QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED', 'STOPPED', 'SUCCESS', 'FAILED', 'TIMED_OUT', 'CANCELLED', 'SKIPPED'];
export const RuntimeWorkbenchPage = () => {
  const user = useCurrentUser(); const permissions = new Set(user.data?.permissions ?? []);
  const [params, setParams] = useSearchParams(); const status = statuses.find(value => value === params.get('status'));
  const taskType = Object.keys(taskTypeLabels).find(value => value === params.get('taskType')) as RuntimeRunFilters['taskType'];
  const initial: RuntimeRunFilters = { batchOnly: params.get('batch') === 'true', taskType, qualityConclusion: params.get('quality') === 'FAILED' ? 'FAILED' : undefined, activeOnly: params.get('active') === 'true', status, from: params.get('from') ?? undefined, to: params.get('to') ?? undefined, timeField: params.get('timeField') === 'endedAt' ? 'endedAt' as const : 'queuedAt' as const };
  const items = [
    { key: 'overview', label: '概览', children: <RuntimeOverviewPanel /> },
    ...(permissions.has('task.view') ? [
      { key: 'runs', label: '运行实例', children: <RuntimeRunsPanel key={JSON.stringify(initial)} initial={initial} /> },
      { key: 'streaming', label: '实时任务', children: <RuntimeStreamingPanel /> },
    ] : []),
    ...(permissions.has('compute.engine.view') ? [{ key: 'engines', label: '计算引擎', children: <RuntimeEnginesPanel /> }] : []),
  ];
  const tab = items.some(i => i.key === params.get('tab')) ? params.get('tab')! : 'overview';
  return <div className="ops-page"><Tabs activeKey={tab} items={items} destroyOnHidden onChange={key => { const next = new URLSearchParams(params); next.set('tab', key); setParams(next); }} /></div>;
};
