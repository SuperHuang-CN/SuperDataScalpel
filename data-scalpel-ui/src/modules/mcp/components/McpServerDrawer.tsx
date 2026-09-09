import { Button, Drawer, Form, Input, Modal, Space, TreeSelect, Typography, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, type DirectoryTreeNode } from '../../directory';
import { useCreateMcpServer, useUpdateMcpServer } from '../hooks/useMcp';
import type { McpServer, SaveMcpServerRequest } from '../model/mcp';

interface Props {
  open: boolean; server?: McpServer | null; directories: DirectoryTreeNode[]; defaultDirectoryId?: string;
  onClose: () => void; onCreated: (server: McpServer) => void;
}
export function McpServerDrawer(props: Props) {
  // Unmount on close, retaining a single local snapshot throughout an editing session.
  return props.open ? <ServerDrawerForm key={props.server?.id ?? 'new'} {...props} /> : null;
}
function ServerDrawerForm({ server, directories, defaultDirectoryId, onClose, onCreated }: Props) {
  const [form] = Form.useForm<SaveMcpServerRequest>();
  const [messageApi, context] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [dirty, setDirty] = useState(false);
  const create = useCreateMcpServer();
  const update = useUpdateMcpServer();
  const saving = create.isPending || update.isPending;
  const close = () => {
    if (saving) return;
    if (!dirty) { onClose(); return; }
    modal.confirm({ title: '放弃未保存的基础信息？', okText: '放弃', cancelText: '继续编辑', onOk: onClose,
      rootClassName: 'business-overlay business-modal-overlay' });
  };
  const submit = async () => {
    if (saving) return;
    try {
      const value = await form.validateFields();
      if (server) {
        await update.mutateAsync({ id: server.id, request: value });
        messageApi.success('MCP Server 已更新'); onClose();
      } else {
        const created = await create.mutateAsync(value);
        onCreated(created);
      }
    } catch (error) { if (error instanceof ApiError) messageApi.error(error.message); }
  };
  return <Drawer open width={560} rootClassName="business-overlay business-drawer-overlay"
    title={server ? '编辑 MCP Server' : '新建 MCP Server'} onClose={close}
    footer={<Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Button disabled={saving} onClick={close}>取消</Button>
      <Button type="primary" loading={saving} onClick={() => void submit()}>确定</Button>
    </Space>}>
    {context}{modalContext}
    <Form form={form} layout="vertical" autoComplete="off" disabled={saving} onValuesChange={() => setDirty(true)}
      initialValues={server ? { name: server.name, directoryId: server.directoryId ?? undefined, description: server.description ?? '', instructions: server.instructions ?? '' }
        : { code: '', name: '', directoryId: defaultDirectoryId, description: '', instructions: '' }}>
      {!server && <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' },
        { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '以小写字母开头，仅支持小写字母、数字、_ 和 -' }]}>
        <Input placeholder="例如 city-insight" maxLength={64} /></Form.Item>}
      <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input maxLength={100} /></Form.Item>
      <Form.Item name="directoryId" label="目录"><TreeSelect allowClear treeDefaultExpandAll treeData={directoryTreeSelectData(directories)} placeholder="可选" /></Form.Item>
      <Form.Item name="description" label="说明"><Input.TextArea autoSize={{ minRows: 3, maxRows: 6 }} maxLength={1000} showCount /></Form.Item>
      <Form.Item name="instructions" label="Server Instructions" tooltip="MCP Client 初始化时可见，用于说明 Server 的定位与使用约束。">
        <Input.TextArea rows={6} maxLength={20000} showCount /></Form.Item>
      <Typography.Text type="secondary">MCP 由 Admin 独立托管。</Typography.Text>
    </Form>
  </Drawer>;
}
