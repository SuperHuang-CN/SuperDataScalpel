import { MoreOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Modal, Select, Space, Tabs, Tag, Tooltip, message } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementListCell } from '../../../shared/components/ManagementListCells';
import { andSearch, searchContains, searchEquals } from '../../../shared/search';
import { useChannels, useOperationsMutation, useRules } from '../hooks/useOperations';
import { commandChannel, commandRule } from '../api/operationsApi';
import { isContinuousRule, ruleLabels, severityLabels, type AlertChannel, type AlertRule, type AlertRuleType } from '../model/operations';
import { OperationsTable } from '../components/OperationsTable';
import { AlertRuleDrawer } from '../components/AlertRuleDrawer';
import { AlertChannelDrawer } from '../components/AlertChannelDrawer';
import { AlertDeliveriesPanel } from '../components/AlertDeliveriesPanel';
import '../components/operations.css';

const RulesPanel = () => {
  const [form] = Form.useForm<{ type?: AlertRuleType; scope?: string }>(); const [filter, setFilter] = useState<{ type?: AlertRuleType; scope?: string }>({});
  const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [editing, setEditing] = useState<AlertRule | null | undefined>();
  const query = useRules({ page, size, sort: 'ruleType,subjectId', search: andSearch(searchEquals('ruleType', filter.type), filter.scope === 'global' ? 'subjectId:null' : filter.scope === 'object' ? 'subjectId!null' : undefined) });
  const command = useOperationsMutation(commandRule); const [notice, context] = message.useMessage(); const [modal, modalContext] = Modal.useModal();
  const run = async (id: string, action: 'enable' | 'disable' | 'reset-override') => { try { await command.mutateAsync({ id, action }); notice.success('规则已更新'); } catch (error) { notice.error(error instanceof Error ? error.message : '更新失败'); } };
  const columns: TableProps<AlertRule>['columns'] = [
    { title: '规则 / 范围', width: 330, render: (_, r) => <ManagementListCell primary={ruleLabels[r.ruleType]} secondary={r.subjectName} /> },
    { title: '状态 / 级别', width: 170, render: (_, r) => <Space><Tag color={r.enabled ? 'success' : 'default'}>{r.enabled ? '开启' : '关闭'}</Tag><Tag color={r.severity === 'CRITICAL' ? 'error' : 'warning'}>{severityLabels[r.severity]}</Tag></Space> },
    { title: '阈值 / 冷却', width: 190, render: (_, r) => <ManagementListCell primary={isContinuousRule(r.ruleType) ? r.thresholdSeconds ? `持续 ${r.thresholdSeconds} 秒` : '尚未设置阈值' : '按运行事件'} secondary={`通知冷却 ${r.cooldownSeconds} 秒`} /> },
    { title: '通知目标', width: 210, render: (_, r) => `站内 ${r.userIds.length} 人 · Webhook ${r.channelIds.length} 个` },
    { title: '操作', width: 70, render: (_, r) => <Dropdown trigger={['click']} menu={{ items: [
      { key: 'edit', label: '编辑规则', onClick: () => setEditing(r) },
      { key: 'toggle', label: r.enabled ? '关闭规则' : '启用规则', onClick: () => void run(r.id, r.enabled ? 'disable' : 'enable') },
      ...(r.subjectId ? [{ key: 'reset', label: '恢复全局默认', onClick: () => modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '恢复全局默认',
        content: `确认移除“${r.subjectName}”的“${ruleLabels[r.ruleType]}”覆盖配置吗？`, okText: '恢复默认', cancelText: '返回', onOk: () => run(r.id, 'reset-override') }) }] : []),
    ] }}><Tooltip title="更多操作"><Button type="text" icon={<MoreOutlined />} loading={command.isPending && command.variables?.id === r.id} aria-label={`操作规则 ${r.subjectName} ${ruleLabels[r.ruleType]}`} /></Tooltip></Dropdown> },
  ];
  return <section className="management-workbench">{context}{modalContext}<div className="management-filter-strip">
    <Form form={form} layout="inline" autoComplete="off" className="management-filter-form" onFinish={v => { setFilter(v); setPage(0); }}>
      <Form.Item name="type"><Select style={{ width: 200 }} allowClear placeholder="全部规则类型" options={Object.entries(ruleLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="scope"><Select style={{ width: 160 }} allowClear placeholder="全部配置范围" options={[{ value: 'global', label: '全局默认' }, { value: 'object', label: '对象覆盖' }]} /></Form.Item>
    </Form><ManagementFilterActions form={form} appliedFilters={filter} onReset={() => { form.resetFields(); setFilter({}); setPage(0); }} /></div>
    <OperationsTable title="告警规则" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }}
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>配置对象覆盖</Button>} />
    {editing !== undefined && <AlertRuleDrawer key={editing?.id ?? 'create'} rule={editing} onClose={() => setEditing(undefined)} />}
  </section>;
};
const ChannelsPanel = () => {
  const [form] = Form.useForm<{ keyword?: string }>(); const [keyword, setKeyword] = useState<string>(); const [page, setPage] = useState(0); const [size, setSize] = useState(20);
  const [editing, setEditing] = useState<AlertChannel | null | undefined>(); const query = useChannels({ page, size, sort: 'name', search: searchContains('name', keyword) });
  const command = useOperationsMutation(commandChannel); const [notice, context] = message.useMessage(); const [params, setParams] = useSearchParams();
  const run = async (id: string, action: 'enable' | 'disable' | 'test') => { try { await command.mutateAsync({ id, action }); notice.success(action === 'test' ? '测试通知已入队，请查看投递记录' : '渠道已更新'); } catch (error) { notice.error(error instanceof Error ? error.message : '操作失败'); } };
  const columns: TableProps<AlertChannel>['columns'] = [
    { title: '通知渠道', width: 240, dataIndex: 'name' },
    { title: '接收地址', width: 420, dataIndex: 'url', ellipsis: true },
    { title: '状态', width: 130, render: (_, c) => <Tag color={c.enabled ? 'success' : 'default'}>{c.enabled ? '开启' : '关闭'}</Tag> },
    { title: '认证', width: 220, render: (_, c) => `Token ${c.bearerTokenConfigured ? '已配置' : '未配置'} · 签名 ${c.hmacSecretConfigured ? '已配置' : '未配置'}` },
    { title: '操作', width: 70, render: (_, c) => <Dropdown trigger={['click']} menu={{ items: [
      { key: 'edit', label: '编辑渠道', onClick: () => setEditing(c) }, { key: 'test', label: '发送测试通知', disabled: !c.enabled, onClick: () => void run(c.id, 'test') },
      { key: 'toggle', label: c.enabled ? '停用渠道' : '启用渠道', onClick: () => void run(c.id, c.enabled ? 'disable' : 'enable') },
      { key: 'history', label: '查看投递记录', onClick: () => { const next = new URLSearchParams(params); next.set('tab', 'deliveries'); next.set('channel', c.id); setParams(next); } },
    ] }}><Tooltip title="更多操作"><Button type="text" icon={<MoreOutlined />} loading={command.isPending && command.variables?.id === c.id} aria-label={`操作渠道 ${c.name}`} /></Tooltip></Dropdown> },
  ];
  return <section className="management-workbench">{context}<div className="management-filter-strip"><Form form={form} layout="inline" autoComplete="off" className="management-filter-form" onFinish={v => { setKeyword(v.keyword); setPage(0); }}>
    <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索通知渠道" /></Form.Item></Form><ManagementFilterActions form={form} appliedFilters={{ keyword }} onReset={() => { form.resetFields(); setKeyword(undefined); setPage(0); }} /></div>
    <OperationsTable title="Webhook 渠道" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }} extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建渠道</Button>} />
    {editing !== undefined && <AlertChannelDrawer key={editing?.id ?? 'create'} channel={editing} onClose={() => setEditing(undefined)} />}
  </section>;
};
export const AlertConfigurationPage = () => {
  const [params, setParams] = useSearchParams(); const tab = ['rules', 'channels', 'deliveries'].includes(params.get('tab') ?? '') ? params.get('tab')! : 'rules';
  return <div className="ops-page"><Tabs activeKey={tab} destroyOnHidden onChange={value => { const next = new URLSearchParams(params); next.set('tab', value); if (value !== 'deliveries') next.delete('channel'); setParams(next); }}
    items={[{ key: 'rules', label: '告警规则', children: <RulesPanel /> }, { key: 'channels', label: 'Webhook 渠道', children: <ChannelsPanel /> },
      { key: 'deliveries', label: '通知投递', children: <AlertDeliveriesPanel key={params.get('channel')} channelId={params.get('channel') ?? undefined} /> }]} /></div>;
};
