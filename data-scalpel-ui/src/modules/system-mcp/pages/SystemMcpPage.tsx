import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { App, Button, Drawer, Form, Select, Space, Switch, Table, Tabs, Tooltip, Typography } from 'antd';
import { AppstoreOutlined, ReloadOutlined, TagsOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../../system';
import { andSearch, searchContains, searchEquals, orSearch } from '../../../shared/search';
import { ManagementListCell, ManagementCode, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import * as api from '../api/systemMcpApi';
import type { Api } from '../model/types';
import { TokenTab } from '../components/TokenTab';
import { AuditTab } from '../components/AuditTab';
import './systemMcp.css';
const effects: Record<string, string> = { READ: '查询', WRITE: '变更', EXECUTE: '执行', UNKNOWN: '未声明' };
const statuses: Record<string, string> = { AVAILABLE: '可开放', INCOMPLETE: '待完善', UNSUPPORTED: '不支持', REMOVED: '已移除' };
export const SystemMcpPage = () => {
 const current = useCurrentUser(); const canEdit = current.data?.permissions.includes('system.mcp.update') ?? false;
 const [activeTab, setActiveTab] = useState('apis');
 const { message } = App.useApp(); const client = useQueryClient(); const [form] = Form.useForm<{ query?: string; effect?: string; status?: string }>();
 const [filters, setFilters] = useState<{ query?: string; effect?: string; status?: string }>({});
 const [module, setModule] = useState<string>();
 const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [changes, setChanges] = useState<Record<string, boolean>>({}); const [enabled, setEnabled] = useState<boolean>();
 const [selected, setSelected] = useState<React.Key[]>([]); const [detail, setDetail] = useState<string>();
 const config = useQuery({ queryKey: ['system-mcp', 'configuration'], queryFn: api.getConfiguration });
 const modules = useQuery({ queryKey: ['system-mcp', 'modules'], queryFn: api.getApiModules });
 const search = andSearch(orSearch(searchContains('summary', filters.query), searchContains('path', filters.query)), searchEquals('module', module), searchEquals('effect', filters.effect), searchEquals('status', filters.status));
 const list = useQuery({ queryKey: ['system-mcp', 'apis', search, page, size], queryFn: () => api.getApis({ search, page, size, sort: 'module,path' }) });
 const description = useQuery({ queryKey: ['system-mcp', 'api', detail], queryFn: () => api.getApi(detail!), enabled: Boolean(detail) });
 const save = useMutation({ mutationFn: () => api.saveConfiguration({ enabled, changes: Object.entries(changes).map(([id, value]) => ({ id, enabled: value })) }), onSuccess: async () => { setChanges({}); setEnabled(undefined); await client.invalidateQueries({ queryKey: ['system-mcp'] }); void message.success('配置已保存'); }, onError: (e: Error) => void message.error(e.message) });
 const refresh = useMutation({ mutationFn: api.refreshCatalog, onSuccess: async () => { await client.invalidateQueries({ queryKey: ['system-mcp'] }); void message.success('接口目录已同步，未保存修改已保留'); }, onError: (e: Error) => void message.error(e.message) });
 const busy = save.isPending || refresh.isPending; const dirty = Object.keys(changes).length > 0 || enabled !== undefined;
 const edit = (ids: React.Key[], value: boolean) => setChanges(old => ({ ...old, ...Object.fromEntries(ids.map(id => [String(id), value])) }));
 const selectModule = (next?: string) => { setModule(next); setPage(0); };
 const apiContent = <section className="management-workbench">
  {config.data?.catalogMessage && config.data.catalogStatus !== 'READY' && <InlineFeedback tone="warning" label={config.data.catalogMessage} />}
  <div className="system-mcp-api-workspace">
   <aside className="system-mcp-module-panel" aria-label="接口分类">
    <div className="system-mcp-module-panel-header"><TagsOutlined /><span>接口分类</span></div>
    <div className="system-mcp-module-list" aria-busy={modules.isFetching}>
     <Button type="text" className={module === undefined ? 'system-mcp-module-active' : undefined} icon={<AppstoreOutlined />} aria-pressed={module === undefined} onClick={() => selectModule()}>
      <span className="system-mcp-module-name">全部接口</span><span className="system-mcp-module-count">{config.data?.totalApis ?? '—'}</span>
     </Button>
     {modules.data?.map(item => <Tooltip key={item.module} placement="right" title={`${item.totalApis} 个接口 · ${item.availableApis} 个可开放 · ${item.enabledApis} 个已开放`}>
      <Button type="text" className={module === item.module ? 'system-mcp-module-active' : undefined} aria-pressed={module === item.module} onClick={() => selectModule(item.module)}>
       <span className="system-mcp-module-name">{item.module}</span><span className="system-mcp-module-count">{item.totalApis}</span>
      </Button>
     </Tooltip>)}
     {modules.error && <InlineFeedback tone="error" label="分类加载失败" action={<Button type="link" onClick={() => void modules.refetch()}>重试</Button>} />}
    </div>
   </aside>
   <div className="system-mcp-api-main management-workbench">
    <div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={v => { setFilters(v); setPage(0); }}><Form.Item name="query"><ManagementSearchInput placeholder="名称或路径" allowClear /></Form.Item><Form.Item name="effect"><Select placeholder="操作性质" allowClear style={{ width: 120 }} options={Object.entries(effects).map(([value, label]) => ({ value, label }))} /></Form.Item><Form.Item name="status"><Select placeholder="可用状态" allowClear style={{ width: 120 }} options={Object.entries(statuses).map(([value, label]) => ({ value, label }))} /></Form.Item></Form><ManagementFilterActions form={form} appliedFilters={filters} loading={list.isFetching} onReset={() => { form.resetFields(); setFilters({}); setPage(0); }} /></div>
    {(list.error || config.error) && <InlineFeedback tone="error" label={list.error?.message ?? config.error?.message} action={<Button onClick={() => { void list.refetch(); void config.refetch(); }}>重试</Button>} />}
    <div className="management-results-surface"><div className="management-result-toolbar"><span>{module ?? '接口列表'} · 共 {list.data?.totalElements ?? 0} 项</span><Space><Tooltip title="刷新列表"><Button icon={<ReloadOutlined />} aria-label="刷新接口列表" onClick={() => void list.refetch()} /></Tooltip>{canEdit && <><Button loading={refresh.isPending} disabled={save.isPending} onClick={() => refresh.mutate()}>同步目录</Button><Button disabled={!selected.length || busy} onClick={() => edit(selected, true)}>批量开放</Button><Button disabled={!selected.length || busy} onClick={() => edit(selected, false)}>批量关闭</Button><Button disabled={!dirty || busy} onClick={() => { setChanges({}); setEnabled(undefined); }}>放弃修改</Button><Button type="primary" disabled={!dirty || refresh.isPending} loading={save.isPending} onClick={() => save.mutate()}>保存{dirty ? `（${Object.keys(changes).length} 项）` : ''}</Button></>}</Space></div>
    <Table<Api> size="small" className="management-table system-mcp-api-table" tableLayout="fixed" rowKey="id" loading={list.isFetching} dataSource={list.data?.content ?? []} scroll={{ y: '100%' }} rowSelection={canEdit ? { columnWidth: 32, selectedRowKeys: selected, onChange: setSelected, preserveSelectedRowKeys: true, getCheckboxProps: row => ({ disabled: row.status !== 'AVAILABLE' || busy }) } : undefined} columns={[
     { title: '接口信息', render: (_, row) => <ManagementListCell className="system-mcp-api-identity" primary={
      <Tooltip title={row.summary}><Button className="system-mcp-api-name" type="link" onClick={() => setDetail(row.id)}>{row.summary}</Button></Tooltip>
     } secondary={<div className="system-mcp-api-summary">
      <Tooltip title={row.description?.trim() || undefined}><span className="system-mcp-api-description">{row.description?.trim() || '—'}</span></Tooltip>
      <div className="system-mcp-api-request">
       <span className={`system-mcp-http-method${row.method === 'GET' ? ' system-mcp-http-method-get' : row.method === 'POST' ? ' system-mcp-http-method-post' : ''}`}>{row.method}</span>
       <span className="system-mcp-api-effect">{effects[row.effect]}</span>
       <ManagementCode value={row.path} title={`${row.method} ${row.path}`} />
      </div>
     </div>} /> },
     { title: '可用状态', width: 100, render: (_, row) => <ManagementStatusIndicator label={statuses[row.status]} tone={row.status === 'AVAILABLE' ? 'success' : row.status === 'INCOMPLETE' ? 'warning' : 'default'} title={row.unavailableReason} /> },
     { title: '开放', width: 64, align: 'center', render: (_, row) => <Switch aria-label={`开放${row.summary}`} checked={changes[row.id] ?? row.enabled} disabled={!canEdit || row.status !== 'AVAILABLE' || busy} onChange={value => edit([row.id], value)} /> },
    ]} pagination={{ current: page + 1, pageSize: size, total: list.data?.totalElements, showSizeChanger: true, hideOnSinglePage: false, onChange: (p, s) => { setPage(s === size ? p - 1 : 0); setSize(s); } }} /></div>
   </div>
  </div>
  <Drawer title={description.data?.summary ?? '接口详情'} open={Boolean(detail)} size={760} onClose={() => setDetail(undefined)}><p>{description.data?.operationId}</p><Typography.Paragraph className="system-mcp-api-detail-description">{description.data?.description || '—'}</Typography.Paragraph><p>{description.data?.unavailableReason}</p>{description.error && <Typography.Text type="danger">{description.error.message}</Typography.Text>}<pre className="system-mcp-contract">{JSON.stringify(description.data?.contract, null, 2)}</pre></Drawer>
 </section>;
 const configurationControls = <div className="system-mcp-configuration" aria-label="系统 MCP 配置">
  <div className="system-mcp-enable-control">
   <span>系统 MCP</span>
   <Tooltip title="调整后需点击接口列表的“保存”才会生效">
    <Switch size="small" aria-label="启用系统 MCP" checked={enabled ?? config.data?.enabled ?? false} disabled={!canEdit || busy || !config.data} onChange={value => setEnabled(value === config.data?.enabled ? undefined : value)} />
   </Tooltip>
   {enabled !== undefined && <span className="system-mcp-pending">待保存</span>}
  </div>
  {config.data?.endpoint && <Typography.Text className="system-mcp-endpoint" copyable={{ text: config.data.endpoint.startsWith('/') ? window.location.origin + config.data.endpoint : config.data.endpoint }}>{config.data.endpoint}</Typography.Text>}
  <Tooltip title={config.data?.catalogMessage}>
   <span className={`system-mcp-catalog-status${config.error || (config.data && config.data.catalogStatus !== 'READY') ? ' system-mcp-catalog-status-warning' : ''}`}>
    {config.error ? '配置加载失败' : config.data?.catalogStatus === 'READY' ? '目录就绪' : config.data?.catalogStatus ?? '加载中'}
   </span>
  </Tooltip>
 </div>;
 return <div className="system-mcp-page"><Tabs activeKey={activeTab} onChange={setActiveTab} tabBarExtraContent={activeTab === 'apis' ? configurationControls : undefined} items={[
  { key: 'apis', label: '接口开放', children: apiContent },
  ...(current.data?.roles.includes('super_admin') ? [{ key: 'tokens', label: '访问令牌', children: <TokenTab canEdit={canEdit} /> }] : []),
  { key: 'audits', label: '操作记录', children: <AuditTab /> },
 ]} /></div>;
};
