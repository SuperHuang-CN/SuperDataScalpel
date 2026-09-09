import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { App, Button, Drawer, Form, Input, Select, Space, Switch, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../../system';
import { andSearch, searchContains, searchEquals, orSearch } from '../../../shared/search';
import { ManagementListCell, ManagementCode } from '../../../shared/components/ManagementListCells';
import * as api from '../api/systemMcpApi';
import type { Api } from '../model/types';
import { TokenTab } from '../components/TokenTab';
import { AuditTab } from '../components/AuditTab';
import './systemMcp.css';
const effects: Record<string, string> = { READ: '查询', WRITE: '变更', EXECUTE: '执行', UNKNOWN: '未声明' };
const statuses: Record<string, string> = { AVAILABLE: '可开放', INCOMPLETE: '待完善', UNSUPPORTED: '不支持', REMOVED: '已移除' };
export const SystemMcpPage = () => {
 const current = useCurrentUser(); const canEdit = current.data?.permissions.includes('system.mcp.update') ?? false;
 return <div className="system-mcp-page"><Tabs items={[
  { key: 'apis', label: '接口开放', children: <ApiTab canEdit={canEdit} /> },
  ...(current.data?.roles.includes('super_admin') ? [{ key: 'tokens', label: '访问令牌', children: <TokenTab canEdit={canEdit} /> }] : []),
  { key: 'audits', label: '操作记录', children: <AuditTab /> },
 ]} /></div>;
};
function ApiTab({ canEdit }: { canEdit: boolean }) {
 const { message } = App.useApp(); const client = useQueryClient(); const [form] = Form.useForm<{ query?: string; module?: string; effect?: string; status?: string }>();
 const [filters, setFilters] = useState<{ query?: string; module?: string; effect?: string; status?: string }>({});
 const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [changes, setChanges] = useState<Record<string, boolean>>({}); const [enabled, setEnabled] = useState<boolean>();
 const [selected, setSelected] = useState<React.Key[]>([]); const [detail, setDetail] = useState<string>();
 const config = useQuery({ queryKey: ['system-mcp', 'configuration'], queryFn: api.getConfiguration });
 const search = andSearch(orSearch(searchContains('summary', filters.query), searchContains('path', filters.query)), searchContains('module', filters.module), searchEquals('effect', filters.effect), searchEquals('status', filters.status));
 const list = useQuery({ queryKey: ['system-mcp', 'apis', search, page, size], queryFn: () => api.getApis({ search, page, size, sort: 'module,path' }) });
 const description = useQuery({ queryKey: ['system-mcp', 'api', detail], queryFn: () => api.getApi(detail!), enabled: Boolean(detail) });
 const save = useMutation({ mutationFn: () => api.saveConfiguration({ enabled, changes: Object.entries(changes).map(([id, value]) => ({ id, enabled: value })) }), onSuccess: async () => { setChanges({}); setEnabled(undefined); await client.invalidateQueries({ queryKey: ['system-mcp'] }); void message.success('配置已保存'); }, onError: (e: Error) => void message.error(e.message) });
 const refresh = useMutation({ mutationFn: api.refreshCatalog, onSuccess: async () => { await client.invalidateQueries({ queryKey: ['system-mcp'] }); void message.success('接口目录已同步，未保存修改已保留'); }, onError: (e: Error) => void message.error(e.message) });
 const busy = save.isPending || refresh.isPending; const dirty = Object.keys(changes).length > 0 || enabled !== undefined;
 const edit = (ids: React.Key[], value: boolean) => setChanges(old => ({ ...old, ...Object.fromEntries(ids.map(id => [String(id), value])) }));
 return <section className="management-workbench">
  <div className="management-filter-strip"><Space wrap><span>系统 MCP</span><Switch checked={enabled ?? config.data?.enabled ?? false} disabled={!canEdit || busy || !config.data} onChange={setEnabled} /><Typography.Text copyable={{ text: config.data?.endpoint.startsWith('/') ? window.location.origin + config.data.endpoint : config.data?.endpoint }}>{config.data?.endpoint}</Typography.Text><Tag>{config.data?.catalogStatus === 'READY' ? '目录已就绪' : config.data?.catalogStatus ?? '加载中'}</Tag><span>{config.data?.catalogMessage}</span></Space></div>
  <div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={v => { setFilters(v); setPage(0); }}><Form.Item name="query"><ManagementSearchInput placeholder="名称或路径" allowClear /></Form.Item><Form.Item name="module"><Input placeholder="业务模块" allowClear /></Form.Item><Form.Item name="effect"><Select placeholder="操作性质" allowClear style={{ width: 120 }} options={Object.entries(effects).map(([value, label]) => ({ value, label }))} /></Form.Item><Form.Item name="status"><Select placeholder="可用状态" allowClear style={{ width: 120 }} options={Object.entries(statuses).map(([value, label]) => ({ value, label }))} /></Form.Item></Form><ManagementFilterActions form={form} appliedFilters={filters} loading={list.isFetching} onReset={() => { form.resetFields(); setFilters({}); setPage(0); }} /></div>
  {(list.error || config.error) && <InlineFeedback tone="error" label={list.error?.message ?? config.error?.message} action={<Button onClick={() => { void list.refetch(); void config.refetch(); }}>重试</Button>} />}
  <div className="management-results-surface"><div className="management-result-toolbar"><span>接口列表 · 共 {list.data?.totalElements ?? 0} 项</span><Space><Tooltip title="刷新列表"><Button icon={<ReloadOutlined />} aria-label="刷新接口列表" onClick={() => void list.refetch()} /></Tooltip>{canEdit && <><Button loading={refresh.isPending} disabled={save.isPending} onClick={() => refresh.mutate()}>同步目录</Button><Button disabled={!selected.length || busy} onClick={() => edit(selected, true)}>批量开放</Button><Button disabled={!selected.length || busy} onClick={() => edit(selected, false)}>批量关闭</Button><Button disabled={!dirty || busy} onClick={() => { setChanges({}); setEnabled(undefined); }}>放弃修改</Button><Button type="primary" disabled={!dirty || refresh.isPending} loading={save.isPending} onClick={() => save.mutate()}>保存{dirty ? `（${Object.keys(changes).length} 项）` : ''}</Button></>}</Space></div>
  <Table<Api> size="small" className="management-table" rowKey="id" loading={list.isFetching} dataSource={list.data?.content ?? []} scroll={{ y: '100%' }} rowSelection={canEdit ? { selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true, getCheckboxProps: row => ({ disabled: row.status !== 'AVAILABLE' || busy }) } : undefined} columns={[
   { title: '接口 / 用途', width: 310, render: (_, row) => <ManagementListCell primary={<Button type="link" onClick={() => setDetail(row.id)}>{row.summary}</Button>} secondary={row.module} /> },
   { title: '方法 / 路径', render: (_, row) => <ManagementCode value={row.operationId} /> },
   { title: '性质', width: 85, render: (_, row) => effects[row.effect] },
   { title: '可用状态', width: 130, render: (_, row) => <Tooltip title={row.unavailableReason}><Tag>{statuses[row.status]}</Tag></Tooltip> },
   { title: '开放', width: 90, render: (_, row) => <Switch aria-label={`开放${row.summary}`} checked={changes[row.id] ?? row.enabled} disabled={!canEdit || row.status !== 'AVAILABLE' || busy} onChange={value => edit([row.id], value)} /> },
  ]} pagination={{ current: page + 1, pageSize: size, total: list.data?.totalElements, showSizeChanger: true, hideOnSinglePage: false, onChange: (p, s) => { setPage(s === size ? p - 1 : 0); setSize(s); } }} /></div>
  <Drawer title={description.data?.summary ?? '接口详情'} open={Boolean(detail)} size={760} onClose={() => setDetail(undefined)}><p>{description.data?.operationId}</p><p>{description.data?.unavailableReason}</p>{description.error && <Typography.Text type="danger">{description.error.message}</Typography.Text>}<pre className="system-mcp-contract">{JSON.stringify(description.data?.contract, null, 2)}</pre></Drawer>
 </section>;
}
