import { DeleteOutlined, LinkOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Input, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useMcpAccessTokens, useServerMcpAccessTokenGrant, useServerMcpAccessTokens } from '../hooks/useMcp';
import type { McpAccessToken } from '../model/mcp';

const escape = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
const formatTime = (value: string | null) => value ? new Date(value).toLocaleString() : '永久有效';

export function McpServerAccessTokenPanel({ serverId }: { serverId: string }) {
  const navigate = useNavigate();
  const [messageApi, context] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerPage, setPickerPage] = useState(0);
  const [pickerKeyword, setPickerKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const authorized = useServerMcpAccessTokens(serverId, { page, size, sort: 'name' });
  const candidates = useMcpAccessTokens({ page: pickerPage, size: 10, sort: 'name', search: appliedKeyword.trim() ? `name:*"${escape(appliedKeyword.trim())}"*` : undefined }, pickerOpen);
  const grant = useServerMcpAccessTokenGrant();
  const authorizedIds = useMemo(() => new Set(authorized.data?.content.map(item => item.id) ?? []), [authorized.data]);

  const revoke = (item: McpAccessToken) => modal.confirm({ title: `撤销“${item.name}”的访问权限？`, content: '只撤销该凭证对当前 Server 的授权，其他 Server 不受影响。', okText: '撤销授权', okButtonProps: { danger: true }, onOk: async () => { await grant.mutateAsync({ serverId, accessTokenId: item.id, action: 'revoke' }); messageApi.success('授权已撤销'); } });
  const columns = [
    { title: '访问凭证', render: (_: unknown, item: McpAccessToken) => <div><Typography.Link strong onClick={() => navigate('/mcp-management/access-tokens')}>{item.name}</Typography.Link><div className="mcp-secondary">{item.hint}</div></div> },
    { title: '状态', dataIndex: 'status', width: 100, render: (value: string) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '已启用' : '已停用'}</Tag> },
    { title: '有效期', dataIndex: 'expiresAt', width: 180, render: formatTime },
    { title: '版本', dataIndex: 'revision', width: 80, render: (value: number) => `r${value}` },
    { title: '操作', width: 90, render: (_: unknown, item: McpAccessToken) => <Button danger type="text" icon={<DeleteOutlined />} onClick={() => revoke(item)}>撤销</Button> },
  ];
  return <div className="business-detail-section">{context}{modalContext}
    <div className="detail-table-toolbar"><div><Typography.Text strong><SafetyCertificateOutlined /> 已授权访问凭证</Typography.Text><div className="mcp-secondary">这些凭证可以调用当前 Server 发布的全部 Tool</div></div><Space><Button onClick={() => navigate('/mcp-management/access-tokens')}>管理凭证</Button><Button type="primary" icon={<LinkOutlined />} onClick={() => setPickerOpen(true)}>关联凭证</Button></Space></div>
    {authorized.isError && <Space className="mcp-query-error"><Typography.Text type="danger">授权凭证加载失败：{authorized.error.message}</Typography.Text><Button onClick={() => void authorized.refetch()}>重试</Button></Space>}
    <Table className="management-table" size="small" rowKey="id" columns={columns} dataSource={authorized.data?.content ?? []} loading={authorized.isLoading} pagination={{ current: page + 1, pageSize: size, total: authorized.data?.totalElements ?? 0, showSizeChanger: true, onChange: (next, nextSize) => { setPage(nextSize === size ? next - 1 : 0); setSize(nextSize); } }} />
    <Modal rootClassName="business-overlay business-modal-overlay" open={pickerOpen} title="关联访问凭证" footer={null} width={720} onCancel={() => setPickerOpen(false)} destroyOnHidden>
      <Space.Compact style={{ width: '100%', marginBottom: 12 }}><Input autoComplete="off" allowClear value={pickerKeyword} onChange={event => setPickerKeyword(event.target.value)} onPressEnter={() => { setAppliedKeyword(pickerKeyword); setPickerPage(0); }} placeholder="搜索凭证名称" /><Button onClick={() => { setAppliedKeyword(pickerKeyword); setPickerPage(0); }}>查询</Button></Space.Compact>
      {candidates.isError && <Typography.Text type="danger">凭证加载失败：{candidates.error instanceof ApiError ? candidates.error.message : '未知错误'}</Typography.Text>}
      <Table<McpAccessToken> className="management-table" size="small" rowKey="id" dataSource={candidates.data?.content ?? []} loading={candidates.isLoading}
        columns={[{ title: '凭证', render: (_, item) => <div><Typography.Text strong>{item.name}</Typography.Text><div className="mcp-secondary">{item.hint}</div></div> }, { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag>{value === 'ENABLED' ? '已启用' : '已停用'}</Tag> }, { title: '操作', width: 100, render: (_, item) => authorizedIds.has(item.id) ? <Typography.Text type="secondary">已关联</Typography.Text> : <Button type="link" loading={grant.isPending} onClick={() => void grant.mutateAsync({ serverId, accessTokenId: item.id, action: 'grant' }).then(() => messageApi.success('凭证已关联')).catch(error => messageApi.error(error instanceof ApiError ? error.message : '关联失败'))}>关联</Button> }]}
        pagination={{ current: pickerPage + 1, pageSize: 10, total: candidates.data?.totalElements ?? 0, onChange: next => setPickerPage(next - 1) }} />
    </Modal>
  </div>;
}
