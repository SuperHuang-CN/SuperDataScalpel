import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { App, Button, Drawer, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { EllipsisOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ManagementDateTime } from '../../../shared/components/ManagementListCells';
import { searchContains } from '../../../shared/search';
import * as api from '../api/systemMcpApi';
import type { Token } from '../model/types';
interface Values { name: string; userId: string; expiresAt?: string }
export function TokenTab({ canEdit }: { canEdit: boolean }) {
 const { message, modal } = App.useApp(); const client = useQueryClient(); const [page, setPage] = useState(0); const [size, setSize] = useState(20);
 const [search, setSearch] = useState<string>(); const [userSearch, setUserSearch] = useState(''); const [editing, setEditing] = useState<Token | 'new'>(); const [secret, setSecret] = useState<string>(); const [form] = Form.useForm<Values>();
 const list = useQuery({ queryKey: ['system-mcp', 'tokens', page, size, search], queryFn: () => api.getTokens({ page, size, search, sort: '-createdAt' }) });
 const users = useQuery({ queryKey: ['system-mcp', 'users', userSearch], queryFn: () => api.getUsers({ page: 0, size: 50, search: searchContains('username', userSearch) }), enabled: editing === 'new' });
 const refresh = () => client.invalidateQueries({ queryKey: ['system-mcp'] });
 const save = useMutation({ mutationFn: async (v: Values) => { const body = { name: v.name, userId: v.userId, expiresAt: v.expiresAt ? new Date(v.expiresAt).toISOString() : undefined }; if (editing === 'new') { const issued = await api.createToken(body); setSecret(issued.secret); } else if (editing) await api.tokenCommand(editing.id, 'update', body); }, onSuccess: async () => { setEditing(undefined); form.resetFields(); await refresh(); void message.success('令牌已保存'); }, onError: (e: Error) => void message.error(e.message) });
 const command = useMutation({ mutationFn: async ({ row, action }: { row: Token; action: 'rotate' | 'enable' | 'disable' | 'delete' }) => { if (action === 'rotate') { const issued = await api.rotateToken(row.id); setSecret(issued.secret); } else await api.tokenCommand(row.id, action); }, onSuccess: async () => { await refresh(); void message.success('操作已完成'); }, onError: (e: Error) => void message.error(e.message) });
 const edit = (row: Token | 'new') => { form.resetFields(); if (row !== 'new') form.setFieldsValue({ name: row.name, userId: row.userId, expiresAt: row.expiresAt ? new Date(new Date(row.expiresAt).getTime() - new Date(row.expiresAt).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : undefined }); setEditing(row); };
 return <section className="management-workbench">
  <div className="management-filter-strip"><Input.Search autoComplete="off" placeholder="令牌名称" allowClear enterButton="查询" onSearch={value => { setSearch(searchContains('name', value)); setPage(0); }} /></div>
  {list.error && <InlineFeedback tone="error" label={list.error.message} action={<Button onClick={() => void list.refetch()}>重试</Button>} />}
  <div className="management-results-surface"><div className="management-result-toolbar"><span>访问令牌 · 共 {list.data?.totalElements ?? 0} 项</span>{canEdit && <Button type="primary" onClick={() => edit('new')}>创建令牌</Button>}</div>
  <Table<Token> size="small" className="management-table" rowKey="id" dataSource={list.data?.content ?? []} loading={list.isFetching} scroll={{ y: '100%' }} columns={[
   { title: '名称', dataIndex: 'name' }, { title: '绑定用户', dataIndex: 'username' },
   { title: '管理方式', render: (_, row) => row.managed ? <Tag>DSH 系统托管</Tag> : '手动' },
   { title: '状态', render: (_, row) => <Tag>{!row.enabled ? '已停用' : row.expiresAt && new Date(row.expiresAt).getTime() < Date.now() ? '已过期' : '启用'}</Tag> },
   { title: '过期时间', render: (_, row) => row.expiresAt ? <ManagementDateTime value={row.expiresAt} /> : '不过期' },
   { title: '最近使用', render: (_, row) => row.lastUsedAt ? <ManagementDateTime value={row.lastUsedAt} /> : '—' },
   { title: '操作', width: 80, render: (_, row) => canEdit && !row.managed ? <Dropdown trigger={['click']} menu={{ items: [
    { key: 'update', label: '修改', onClick: () => edit(row) },
    { key: 'status', label: row.enabled ? '停用' : '启用', onClick: () => command.mutate({ row, action: row.enabled ? 'disable' : 'enable' }) },
    { key: 'rotate', label: '轮换', onClick: () => modal.confirm({ title: `轮换“${row.name}”？`, content: '旧令牌将立即失效，新令牌仅显示一次。', onOk: () => command.mutateAsync({ row, action: 'rotate' }) }) },
    { key: 'delete', label: '删除', danger: true, onClick: () => modal.confirm({ title: `删除“${row.name}”？`, content: '使用此令牌的后续请求将失效。', onOk: () => command.mutateAsync({ row, action: 'delete' }) }) },
   ] }}><Button type="text" icon={<EllipsisOutlined />} aria-label={`${row.name}的操作`} loading={command.isPending && command.variables?.row.id === row.id} /></Dropdown> : '—' },
  ]} pagination={{ current: page + 1, pageSize: size, total: list.data?.totalElements, showSizeChanger: true, hideOnSinglePage: false, onChange: (p, s) => { setPage(s === size ? p - 1 : 0); setSize(s); } }} /></div>
  <Drawer title={editing === 'new' ? '创建令牌' : '修改令牌'} open={Boolean(editing)} onClose={() => setEditing(undefined)} footer={<Space><Button onClick={() => setEditing(undefined)}>取消</Button><Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存</Button></Space>}><Form form={form} autoComplete="off" layout="vertical" onFinish={v => save.mutate(v)}><Form.Item name="name" label="名称" rules={[{ required: true }, { max: 100 }]}><Input /></Form.Item>{editing === 'new' && <Form.Item name="userId" label="绑定用户" rules={[{ required: true }]}><Select showSearch filterOption={false} onSearch={setUserSearch} loading={users.isFetching} options={users.data?.content.map(u => ({ value: u.id, label: `${u.displayName || u.username}（${u.username}）` }))} /></Form.Item>}<Form.Item name="expiresAt" label="过期时间（留空表示不过期）"><Input type="datetime-local" /></Form.Item>{users.error && <Typography.Text type="danger">{users.error.message}</Typography.Text>}</Form></Drawer>
  <Modal title="完整令牌仅显示这一次" open={Boolean(secret)} onCancel={() => setSecret(undefined)} footer={<Button type="primary" onClick={() => setSecret(undefined)}>已保存，关闭</Button>} destroyOnHidden><BusinessSecretInput readOnly value={secret} /><Typography.Paragraph copyable={{ text: secret }}>复制令牌</Typography.Paragraph></Modal>
 </section>;
}
