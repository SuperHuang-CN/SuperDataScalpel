import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import { CopyOutlined, DeleteOutlined, EditOutlined, EyeOutlined, KeyOutlined, MoreOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Dropdown, Input, Modal, Space, Table, Tag, Tooltip, Typography, message, type MenuProps, type TableColumnsType } from 'antd';
import { useState } from 'react';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ApiError } from '../../../shared/api/http';
import { McpAccessTokenDrawer } from '../components/McpAccessTokenDrawer';
import { useDeleteMcpAccessToken, useMcpAccessTokenAction, useMcpAccessTokens, useRotateMcpAccessToken } from '../hooks/useMcp';
import type { McpAccessToken } from '../model/mcp';
import { fetchMcpAccessTokenSecret } from '../api/mcpApi';
import './mcp.css';

const escape = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString() : '—';
const tokenState = (item: McpAccessToken) => item.status === 'DISABLED'
  ? { color: 'default', label: '已停用' }
  : item.expiresAt && new Date(item.expiresAt).getTime() <= Date.now()
    ? { color: 'error', label: '已过期' }
    : { color: 'success', label: '已启用' };

export function McpAccessTokenPage() {
  const [messageApi, context] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editor, setEditor] = useState<string | null | undefined>(undefined);
  const [secret, setSecret] = useState<string | null>(null);
  const [secretLoading, setSecretLoading] = useState(false);
  const query = useMcpAccessTokens({ page, size, sort: '-updatedAt', search: appliedKeyword.trim() ? `(name:*"${escape(appliedKeyword.trim())}"* OR tokenHint:*"${escape(appliedKeyword.trim())}"*)` : undefined });
  const action = useMcpAccessTokenAction();
  const rotate = useRotateMcpAccessToken();
  const remove = useDeleteMcpAccessToken();

  const reveal = async (id: string) => {
    setSecretLoading(true);
    try { setSecret((await fetchMcpAccessTokenSecret(id)).accessToken); }
    catch (error) { messageApi.error(error instanceof ApiError ? error.message : 'Token 加载失败'); }
    finally { setSecretLoading(false); }
  };
  const rotateToken = (item: McpAccessToken) => modal.confirm({ title: `轮换“${item.name}”？`, content: '旧 Token 会立即失效，客户端需要改用新 Token。', okText: '确认轮换', okButtonProps: { danger: true }, onOk: async () => { const issued = await rotate.mutateAsync(item.id); setSecret(issued.accessToken); messageApi.success('Token 已轮换'); } });
  const deleteToken = (item: McpAccessToken) => modal.confirm({ title: `删除“${item.name}”？`, content: '删除后 Token 立即失效，Server 授权一并删除；历史调用日志仍保留。', okText: '删除', okButtonProps: { danger: true }, onOk: async () => { await remove.mutateAsync(item.id); messageApi.success('访问凭证已删除'); } });
  const menu = (item: McpAccessToken): MenuProps['items'] => [
    { key: 'toggle', label: item.status === 'ENABLED' ? '停用' : '启用', onClick: () => void action.mutateAsync({ id: item.id, action: item.status === 'ENABLED' ? 'disable' : 'enable' }).then(() => messageApi.success(item.status === 'ENABLED' ? '已停用' : '已启用')) },
    { key: 'rotate', icon: <SyncOutlined />, label: '轮换 Token', onClick: () => rotateToken(item) },
    { type: 'divider' },
    { key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', onClick: () => deleteToken(item) },
  ];
  const columns: TableColumnsType<McpAccessToken> = [
    { title: '访问凭证', render: (_, item) => <div><Typography.Link strong onClick={() => setEditor(item.id)}>{item.name}</Typography.Link><div className="mcp-secondary">{item.description || '暂无说明'}</div></div> },
    { title: 'Token', dataIndex: 'hint', width: 190, render: (hint: string, item) => <Space size={2}><Typography.Text code>{hint}</Typography.Text><Tooltip title="查看完整 Token"><Button type="text" size="small" icon={<EyeOutlined />} loading={secretLoading} aria-label={`查看${item.name}的完整 Token`} onClick={() => void reveal(item.id)} /></Tooltip><Tooltip title="复制完整 Token"><Button type="text" size="small" icon={<CopyOutlined />} aria-label={`复制${item.name}的完整 Token`} onClick={() => void fetchMcpAccessTokenSecret(item.id).then(value => writeClipboardText(value.accessToken)).then(() => messageApi.success('Token 已复制')).catch(error => messageApi.error(error instanceof ApiError ? error.message : '复制失败'))} /></Tooltip></Space> },
    { title: '状态', dataIndex: 'status', width: 100, render: (_, item) => { const state = tokenState(item); return <Tag color={state.color}>{state.label}</Tag>; } },
    { title: '授权', dataIndex: 'authorizedServerCount', width: 100, align: 'right', render: value => `${value} 个` },
    { title: '有效期', dataIndex: 'expiresAt', width: 180, render: formatTime },
    { title: '最近调用', dataIndex: 'lastUsedAt', width: 180, render: formatTime },
    { title: '操作', width: 110, fixed: 'right', render: (_, item) => <Space size={2}><Tooltip title="编辑"><Button type="text" icon={<EditOutlined />} aria-label={`编辑${item.name}`} onClick={() => setEditor(item.id)} /></Tooltip><Dropdown menu={{ items: menu(item) }} trigger={['click']}><Button type="text" icon={<MoreOutlined />} aria-label={`${item.name}更多操作`} /></Dropdown></Space> },
  ];

  return <section className="management-workbench mcp-management-workbench">
    {context}{modalContext}
    <div className="management-filter-strip"><Input autoComplete="off" allowClear value={keyword} onChange={event => setKeyword(event.target.value)} onPressEnter={() => { setAppliedKeyword(keyword); setPage(0); }} placeholder="凭证名称或 Token 提示" style={{ width: 320 }} /><Space style={{ marginLeft: 'auto' }}><Button type="primary" onClick={() => { setAppliedKeyword(keyword); setPage(0); }}>查询</Button>{(keyword || appliedKeyword) && <Button type="text" onClick={() => { setKeyword(''); setAppliedKeyword(''); setPage(0); }}>重置</Button>}</Space></div>
    <div className="management-results-surface"><div className="management-result-toolbar"><div className="management-result-title"><SafetyCertificateOutlined /> 访问凭证 <span className="management-result-count">共 {query.data?.totalElements ?? 0} 项</span></div><Space><Tooltip title="刷新"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新访问凭证" onClick={() => void query.refetch()} /></Tooltip><Button type="primary" icon={<PlusOutlined />} onClick={() => setEditor(null)}>新建凭证</Button></Space></div>
      {query.isError && <Space className="mcp-query-error"><Typography.Text type="danger">访问凭证加载失败：{query.error.message}</Typography.Text><Button onClick={() => void query.refetch()}>重试</Button></Space>}
      <Table className="management-table management-table-comfortable" size="small" rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isLoading} scroll={{ y: 'calc(100vh - 290px)' }} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true, showTotal: total => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize === size ? next - 1 : 0); setSize(nextSize); } }} />
    </div>
    <McpAccessTokenDrawer open={editor !== undefined} tokenId={editor} onClose={() => setEditor(undefined)} onIssued={value => setSecret(value)} />
    <Modal rootClassName="business-overlay business-modal-overlay" open={secret !== null} title={<Space><KeyOutlined />完整 Access Token</Space>} footer={<Space><Button icon={<CopyOutlined />} onClick={() => void writeClipboardText(secret ?? '').then(() => messageApi.success('Token 已复制'))}>复制</Button><Button type="primary" onClick={() => setSecret(null)}>关闭</Button></Space>} onCancel={() => setSecret(null)} destroyOnHidden><BusinessSecretInput readOnly name="mcp-access-token-secret" value={secret ?? ''} /></Modal>
  </section>;
}
