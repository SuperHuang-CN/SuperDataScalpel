import { BellOutlined } from '@ant-design/icons';
import { Button, Form, Input, Select, Tag } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { useAlerts } from '../hooks/useOperations';
import { buildAlertSearch, type AlertFilters } from '../model/operationsSearch';
import { ruleLabels, severityLabels, statusLabels, type AlertIncident, type AlertRuleType } from '../model/operations';
import { OperationsTable } from '../components/OperationsTable';
import { AlertIncidentDrawer } from '../components/AlertIncidentDrawer';
import '../components/operations.css';

type AdvancedFilters = Pick<AlertFilters, 'ruleType' | 'severity'> & { fromLocal?: string; toLocal?: string };
const emptyAdvanced: AdvancedFilters = { ruleType: undefined, severity: undefined, fromLocal: undefined, toLocal: undefined };

export const AlertCenterPage = () => {
  const [params, setParams] = useSearchParams();
  const initialType = Object.keys(ruleLabels).find(type => type === params.get('ruleType')) as AlertRuleType | undefined;
  const initial: AlertFilters = { status: 'ACTIVE', ruleType: initialType };
  const [form] = Form.useForm<AlertFilters>(); const [filters, setFilters] = useState<AlertFilters>(initial);
  const [advancedForm] = Form.useForm<AdvancedFilters>();
  const [advanced, setAdvanced] = useState<AdvancedFilters>({ ...emptyAdvanced, ruleType: initialType });
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [page, setPage] = useState(0); const [size, setSize] = useState(20);
  const incidentId = params.get('incident'); const query = useAlerts({ page, size, sort: '-occurredAt', search: buildAlertSearch(filters) });
  const open = (id: string | null) => { const next = new URLSearchParams(params); if (id) next.set('incident', id); else next.delete('incident'); setParams(next); };
  const columns: TableProps<AlertIncident>['columns'] = [
    { title: '告警 / 对象', width: 300, render: (_, i) => <ManagementListCell icon={<BellOutlined />} primary={<Button type="link" className="ops-name-button" onClick={() => open(i.id)}>{i.subjectName}</Button>} secondary={i.summary} /> },
    { title: '类型 / 级别', width: 190, render: (_, i) => <ManagementListCell primary={ruleLabels[i.ruleType]} secondary={<Tag color={i.severity === 'CRITICAL' ? 'error' : 'warning'}>{severityLabels[i.severity]}</Tag>} /> },
    { title: '处理状态', width: 140, render: (_, i) => <ManagementListCell primary={<Tag color={i.status === 'OPEN' ? 'processing' : 'default'}>{statusLabels[i.status]}</Tag>} secondary={i.silencedUntil ? '静默中' : i.conditionState === 'UNKNOWN' ? '观测待确认' : null} /> },
    { title: '发生时间', dataIndex: 'occurredAt', width: 170, render: v => <ManagementDateTime value={v} /> },
    { title: '通知状态', width: 185, render: (_, i) => <ManagementListCell primary={i.notifications.failed ? <Tag color="error">Webhook 失败 {i.notifications.failed}</Tag> : i.notifications.pending ? `Webhook 待投递 ${i.notifications.pending}` : i.notifications.sent ? `Webhook 已送达 ${i.notifications.sent}` : '暂无 Webhook 投递'} secondary={`站内 ${i.notifications.inApp} · 抑制 ${i.notifications.suppressed}`} /> },
    { title: '关闭 / 处理结果', width: 180, render: (_, i) => <ManagementListCell primary={i.closedAt ? new Date(i.closedAt).toLocaleString() : '—'} secondary={i.closeReason} /> },
  ];
  return <div className="ops-page"><section className="management-workbench">
    <div className="management-filter-strip"><Form form={form} initialValues={initial} autoComplete="off" layout="inline" className="management-filter-form" onFinish={values => { const a = advancedOpen ? advanced : advancedForm.getFieldsValue(true); setAdvanced(a); setFilters({ ...values, ruleType: a.ruleType, severity: a.severity, from: a.fromLocal ? new Date(a.fromLocal).toISOString() : undefined, to: a.toLocal ? new Date(a.toLocal).toISOString() : undefined }); setPage(0); }}>
      <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索对象或告警摘要" /></Form.Item>
      <Form.Item name="status"><Select style={{ width: 130 }} options={[{ value: 'ACTIVE', label: '未关闭' }, ...Object.entries(statusLabels).map(([value, label]) => ({ value, label })), { value: 'ALL', label: '全部状态' }]} /></Form.Item>
    </Form><ManagementAdaptiveMoreFilters count={Object.values(advanced).filter(Boolean).length} open={advancedOpen}
      onOpenChange={open => { if (open) setAdvanced(advancedForm.getFieldsValue(true)); else advancedForm.setFieldsValue(advanced); setAdvancedOpen(open); }}
      onClear={() => advancedForm.setFieldsValue(emptyAdvanced)} onCancel={() => { advancedForm.setFieldsValue(advanced); setAdvancedOpen(false); }}
      onConfirm={() => { setAdvanced(advancedForm.getFieldsValue(true)); setAdvancedOpen(false); }}>
      <Form form={advancedForm} initialValues={{ ...emptyAdvanced, ruleType: initialType }} autoComplete="off" layout="vertical">
      <Form.Item name="ruleType" label="告警类型"><Select allowClear placeholder="全部告警类型" style={{ width: 175 }} options={Object.entries(ruleLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="severity" label="告警级别"><Select allowClear placeholder="全部级别" style={{ width: 120 }} options={Object.entries(severityLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="fromLocal" label="发生时间起点"><Input type="datetime-local" aria-label="告警发生开始时间" style={{ width: 190 }} /></Form.Item>
      <Form.Item name="toLocal" label="发生时间终点"><Input type="datetime-local" aria-label="告警发生结束时间" style={{ width: 190 }} /></Form.Item>
    </Form></ManagementAdaptiveMoreFilters><ManagementFilterActions form={form} appliedFilters={filters} loading={query.isFetching} onReset={() => { form.resetFields(); form.setFieldsValue({ status: 'ACTIVE', ruleType: undefined }); advancedForm.setFieldsValue(emptyAdvanced); setAdvanced(emptyAdvanced); setAdvancedOpen(false); setFilters({ status: 'ACTIVE' }); setPage(0); }} /></div>
    <OperationsTable title="告警记录" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }} />
  </section><AlertIncidentDrawer key={incidentId} incidentId={incidentId} onClose={() => open(null)} /></div>;
};
