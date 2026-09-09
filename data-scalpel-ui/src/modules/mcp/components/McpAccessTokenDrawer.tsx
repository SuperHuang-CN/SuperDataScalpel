import { KeyOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateMcpAccessToken, useMcpAccessToken, useMcpAccessTokenServerCandidates, useUpdateMcpAccessToken, useUpdateMcpAccessTokenServers } from '../hooks/useMcp';
import { mcpStatusLabels, type McpAuthorizedServer } from '../model/mcp';

interface FormValues { name: string; description?: string; expiresAt?: string }
interface Props {
  open: boolean;
  tokenId?: string | null;
  onClose: () => void;
  onIssued: (token: string) => void;
}
const escape = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
const toLocalDateTime = (value: string) => {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};

export function McpAccessTokenDrawer(props: Props) {
  return props.open ? <McpAccessTokenDrawerForm key={props.tokenId ?? 'new'} {...props} /> : null;
}

function McpAccessTokenDrawerForm({ tokenId, onClose, onIssued }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [messageApi, context] = message.useMessage();
  const detail = useMcpAccessToken(tokenId ?? undefined);
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [selectionOverrides, setSelectionOverrides] = useState<Map<string, McpAuthorizedServer | null>>(new Map());
  const search = useMemo(() => appliedKeyword.trim()
    ? `(name:*"${escape(appliedKeyword.trim())}"* OR code:*"${escape(appliedKeyword.trim())}"*)` : undefined, [appliedKeyword]);
  const servers = useMcpAccessTokenServerCandidates({ page, size, sort: 'name', search });
  const create = useCreateMcpAccessToken();
  const update = useUpdateMcpAccessToken();
  const updateServers = useUpdateMcpAccessTokenServers();
  const saving = create.isPending || update.isPending || updateServers.isPending;
  const selected = useMemo(() => {
    const next = new Map(detail.data?.authorizedServers.map(server => [server.id, server]) ?? []);
    selectionOverrides.forEach((server, id) => { if (server) next.set(id, server); else next.delete(id); });
    return next;
  }, [detail.data?.authorizedServers, selectionOverrides]);

  useEffect(() => {
    if (!detail.data) return;
    const value = detail.data.token;
    form.setFieldsValue({ name: value.name, description: value.description ?? '', expiresAt: value.expiresAt ? toLocalDateTime(value.expiresAt) : undefined });
  }, [detail.data, form]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const basic = { name: values.name, description: values.description, expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : undefined };
      const serverIds = [...selected.keys()];
      if (tokenId) {
        await update.mutateAsync({ id: tokenId, request: basic });
        await updateServers.mutateAsync({ id: tokenId, serverIds });
        messageApi.success('访问凭证已更新');
        onClose();
      } else {
        const issued = await create.mutateAsync({ ...basic, serverIds });
        onIssued(issued.accessToken);
        onClose();
      }
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
    }
  };

  const rows = servers.data?.content ?? [];
  return <Drawer open width={760} rootClassName="business-overlay business-drawer-overlay" title={tokenId ? '编辑访问凭证' : '新建访问凭证'}
    onClose={onClose} closable={!saving} maskClosable={!saving} keyboard={!saving} footer={<div className="mcp-drawer-footer"><Typography.Text type="secondary">已授权 {selected.size} 个 Server</Typography.Text><Space><Button disabled={saving} onClick={onClose}>取消</Button><Button type="primary" loading={saving} onClick={() => void submit()}>确定</Button></Space></div>}>
    {context}
    {tokenId && detail.isLoading ? <div>正在加载凭证…</div> : tokenId && detail.isError ? <Space><Typography.Text type="danger">凭证加载失败：{detail.error.message}</Typography.Text><Button onClick={() => void detail.refetch()}>重试</Button></Space> : <Form form={form} layout="vertical" autoComplete="off" initialValues={{ name: '', description: '', expiresAt: undefined }}>
      <div className="mcp-drawer-title"><KeyOutlined /><div><Typography.Text strong>凭证信息</Typography.Text><div className="mcp-secondary">用于识别调用方，可随时调整有效期和 Server 授权</div></div></div>
      <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入凭证名称' }]}><Input maxLength={100} /></Form.Item>
      <Form.Item name="description" label="说明"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} maxLength={1000} showCount /></Form.Item>
      <Form.Item name="expiresAt" label="有效期" tooltip="留空表示永久有效"><Input type="datetime-local" /></Form.Item>
      <div className="mcp-token-server-picker">
        <div className="mcp-token-server-toolbar"><div><Typography.Text strong>授权 Server</Typography.Text><Typography.Text type="secondary"> · 已选 {selected.size} 项</Typography.Text></div><Space.Compact><Input autoComplete="off" allowClear prefix={<SearchOutlined />} value={keyword} onChange={event => setKeyword(event.target.value)} onPressEnter={() => { setAppliedKeyword(keyword); setPage(0); }} placeholder="名称或编码" /><Button onClick={() => { setAppliedKeyword(keyword); setPage(0); }}>查询</Button></Space.Compact></div>
        <Table<McpAuthorizedServer> className="management-table" size="small" rowKey="id" loading={servers.isLoading} dataSource={rows}
          rowSelection={{ type: 'checkbox', preserveSelectedRowKeys: true, selectedRowKeys: [...selected.keys()], onSelect: (record, checked) => setSelectionOverrides(current => { const next = new Map(current); next.set(record.id, checked ? record : null); return next; }), onSelectAll: (checked, changed) => setSelectionOverrides(current => { const next = new Map(current); changed.forEach(record => next.set(record.id, checked ? record : null)); return next; }) }}
          columns={[{ title: 'Server', render: (_, server) => <div><Typography.Text strong>{server.name}</Typography.Text><div className="mcp-secondary">{server.code}</div></div> }, { title: '状态', dataIndex: 'status', width: 100, render: (_, server) => <Tag>{mcpStatusLabels[server.status]}</Tag> }]}
          pagination={{ current: page + 1, pageSize: size, total: servers.data?.totalElements ?? 0, showSizeChanger: true, onChange: (next, nextSize) => { setPage(nextSize === size ? next - 1 : 0); setSize(nextSize); } }} />
      </div>
    </Form>}
  </Drawer>;
}
