import { ApiOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, Space, Switch, message } from 'antd';
import { useState } from 'react';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { saveChannel } from '../api/operationsApi';
import { useOperationsMutation } from '../hooks/useOperations';
import type { AlertChannel, AlertChannelWrite } from '../model/operations';

export const AlertChannelDrawer = ({ channel, onClose }: { channel: AlertChannel | null; onClose: () => void }) => {
  const [form] = Form.useForm<AlertChannelWrite>(); const [bearerOpen, setBearerOpen] = useState(false); const [hmacOpen, setHmacOpen] = useState(false);
  const save = useOperationsMutation(saveChannel); const [notice, context] = message.useMessage();
  return <>{context}<Drawer rootClassName="business-overlay business-drawer-overlay" open onClose={onClose} width={680}
    title={<Space><ApiOutlined />{channel ? '编辑 Webhook 渠道' : '新建 Webhook 渠道'}</Space>}
    footer={<Space style={{ display: 'flex', justifyContent: 'flex-end' }}><Button onClick={onClose}>取消</Button><Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存</Button></Space>}>
    <Form form={form} layout="vertical" autoComplete="off" initialValues={{ name: channel?.name, url: channel?.url, enabled: channel?.enabled ?? true }}
      onFinish={async values => { try { await save.mutateAsync({ id: channel?.id, body: values }); notice.success('通知渠道已保存'); onClose(); } catch (error) { notice.error(error instanceof Error ? error.message : '保存失败'); } }}>
      <Form.Item name="name" label="渠道名称" rules={[{ required: true, whitespace: true }, { max: 150 }]}><Input maxLength={150} /></Form.Item>
      <Form.Item name="url" label="接收地址" rules={[{ required: true, whitespace: true }, { validator: async (_, value: string) => { if (!value) return; const url = new URL(value); if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash) throw new Error('请输入有效的 HTTP/HTTPS 接收地址'); } }]}><Input placeholder="https://example.internal/hooks/data-scalpel" maxLength={2000} /></Form.Item>
      <Form.Item name="enabled" label="启用通知渠道" valuePropName="checked"><Switch /></Form.Item>
      <div className="ops-panel-toolbar"><span>Bearer Token：{channel?.bearerTokenConfigured ? '已保存' : '未配置'}</span><Button type="link" onClick={() => setBearerOpen(v => !v)}>{bearerOpen ? '收起' : channel?.bearerTokenConfigured ? '替换' : '配置'}</Button></div>
      {bearerOpen && <Form.Item name="bearerToken" label="新 Token"><BusinessSecretInput name="alert-channel-bearer-secret" maxLength={4000} /></Form.Item>}
      {channel?.bearerTokenConfigured && <Form.Item name="clearBearerToken" label="清除已保存 Token" valuePropName="checked"><Switch /></Form.Item>}
      <div className="ops-panel-toolbar"><span>HMAC 签名密钥：{channel?.hmacSecretConfigured ? '已保存' : '未配置'}</span><Button type="link" onClick={() => setHmacOpen(v => !v)}>{hmacOpen ? '收起' : channel?.hmacSecretConfigured ? '替换' : '配置'}</Button></div>
      {hmacOpen && <Form.Item name="hmacSecret" label="新签名密钥"><BusinessSecretInput name="alert-channel-hmac-secret" maxLength={4000} /></Form.Item>}
      {channel?.hmacSecretConfigured && <Form.Item name="clearHmacSecret" label="清除已保存签名密钥" valuePropName="checked"><Switch /></Form.Item>}
      <ContextHelp ariaLabel="Webhook 投递约定" presentation="popover" content="固定 JSON POST，接收端按 deliveryId 去重。支持 Bearer Token 和 HMAC-SHA256 签名；连接超时 3 秒、总超时 5 秒。保存只更新配置，点击测试才会发送测试消息。修改接收地址或凭据后，旧待发投递将被抑制。" />
    </Form>
  </Drawer></>;
};
