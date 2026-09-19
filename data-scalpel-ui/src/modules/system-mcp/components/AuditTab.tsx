import { ManagementFilterActions } from '../../../shared/components/ManagementFilters';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { Button, Drawer, Form, Input, Select, Table } from 'antd';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { andSearch, searchContains, searchEquals, searchComparison } from '../../../shared/search';
import { ManagementDateTime, ManagementCode } from '../../../shared/components/ManagementListCells';
import { getAudits } from '../api/systemMcpApi';
import type { Audit } from '../model/types';
interface Filters { operation?: string; username?: string; tokenId?: string; status?: string; start?: string; end?: string }
export function AuditTab() {
 const [form] = Form.useForm<Filters>(); const [search, setSearch] = useState<string>(); const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [detail, setDetail] = useState<Audit>();
 const list = useQuery({ queryKey: ['system-mcp', 'audits', search, page, size], queryFn: () => getAudits({ search, page, size, sort: '-createdAt' }) });
 const apply = (v: Filters) => { setSearch(andSearch(searchContains('operationId', v.operation), searchContains('username', v.username), searchEquals('tokenId', v.tokenId), searchEquals('status', v.status), searchComparison('createdAt', '>=', v.start ? new Date(v.start).toISOString() : undefined), searchComparison('createdAt', '<=', v.end ? new Date(v.end).toISOString() : undefined))); setPage(0); };
 return <section className="management-workbench"><div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={apply}><Form.Item name="operation"><Input placeholder="接口标识" /></Form.Item><Form.Item name="username"><Input placeholder="操作用户" /></Form.Item><Form.Item name="tokenId"><Input placeholder="令牌 ID" /></Form.Item><Form.Item name="status"><Select allowClear placeholder="状态" style={{ width: 100 }} options={[{ value: 'SUCCESS', label: '成功' }, { value: 'ERROR', label: '错误' }]} /></Form.Item><Form.Item name="start"><Input type="datetime-local" aria-label="开始时间" /></Form.Item><Form.Item name="end"><Input type="datetime-local" aria-label="结束时间" /></Form.Item></Form><ManagementFilterActions form={form} additionalActive={Boolean(search)} loading={list.isFetching} onReset={() => { form.resetFields(); apply({}); }} /></div>
 {list.error && <InlineFeedback tone="error" label={list.error.message} action={<Button onClick={() => void list.refetch()}>重试</Button>} />}
 <div className="management-results-surface"><div className="management-result-toolbar">操作记录 · 共 {list.data?.totalElements ?? 0} 项</div><Table<Audit> rowKey="id" size="small" className="management-table" loading={list.isFetching} dataSource={list.data?.content ?? []} scroll={{ y: '100%' }} columns={[
  { title: '时间', width: 155, render: (_, r) => <ManagementDateTime value={r.createdAt} /> }, { title: '用户', dataIndex: 'username', width: 110 },
  { title: '事件', width: 170, render: (_, r) => <Button type="link" onClick={() => setDetail(r)}>{r.toolName ?? r.eventType}</Button> },
  { title: '接口', render: (_, r) => <ManagementCode value={r.operationId ?? '—'} /> }, { title: '状态', width: 100, render: (_, r) => r.status === 'SUCCESS' ? '成功' : '错误' }, { title: '耗时 / ms', dataIndex: 'durationMs', width: 110, align: 'right' },
 ]} pagination={{ current: page + 1, pageSize: size, total: list.data?.totalElements, showSizeChanger: true, hideOnSinglePage: false, onChange: (p, s) => { setPage(s === size ? p - 1 : 0); setSize(s); } }} /></div>
 <Drawer title="操作记录详情" open={Boolean(detail)} onClose={() => setDetail(undefined)}><pre className="system-mcp-contract">{JSON.stringify(detail, null, 2)}</pre></Drawer></section>;
}
