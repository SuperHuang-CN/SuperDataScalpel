import { useEffect } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ClockCircleOutlined } from '@ant-design/icons';
import { Button, Input, Drawer, Form, InputNumber, Space, Tag, message } from 'antd';
import { CompactAlert } from '../../../shared/components/ContextualFeedback';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { getGatewayValidity, saveGatewayValidity, type GatewayValidityKind } from '../api/gatewayValidityApi';

export interface GatewayValidityTarget { kind: GatewayValidityKind; id: string; name: string }
const localTime = (value: string | null) => value ? new Date(Date.parse(value) - new Date(value).getTimezoneOffset() * 60_000).toISOString().slice(0, -1) : '';
export const GatewayValidityDrawer = ({ target, onClose }: { target: GatewayValidityTarget | null; onClose: () => void }) => {
  const [form] = Form.useForm<{ validFrom: string; expiresAt: string; requestsPerSecond: number }>();
  const query = useQuery({ queryKey: ['gateway-validity', target?.kind, target?.id], queryFn: () => getGatewayValidity(target!.kind, target!.id), enabled: !!target });
  useEffect(() => { if (query.data) form.setFieldsValue({ validFrom: localTime(query.data.validFrom), expiresAt: localTime(query.data.expiresAt), requestsPerSecond: query.data.requestsPerSecond }); }, [query.data, form]);
  const save = useMutation({ mutationFn: (values: { validFrom: string; expiresAt: string; requestsPerSecond: number }) => saveGatewayValidity(target!.kind, target!.id, {
    validFrom: values.validFrom ? new Date(values.validFrom).toISOString() : null, expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : null, requestsPerSecond: values.requestsPerSecond ?? 0,
  }), onSuccess: () => { message.success('有效期已保存，节点加载后生效；到期无需再次同步'); void query.refetch(); }, onError: (error: Error) => message.error(error.message) });
  return <Drawer closable={{ placement: 'end' }} title={<OverlayTitle title={`${target?.name ?? ''} · 有效期与续期`} icon={<ClockCircleOutlined />} />} size={620} open={!!target} onClose={onClose} rootClassName="business-overlay business-drawer-overlay resource-workspace-overlay"
    footer={<Space style={{ display: 'flex', justifyContent: 'flex-end' }}><Button onClick={onClose}>关闭</Button><Button type="primary" disabled={!query.data} loading={save.isPending} onClick={() => form.submit()}>保存</Button></Space>}>
    <CompactAlert type="info" message="仅支持自研网关。时间使用本地时区显示，到期时每次调用直接拒绝；续期不会重新启用已撤回对象，也不会更换 API Key。" />
    {query.error && <CompactAlert type="error" message={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
    {query.data && <Space><Tag>{({ ACTIVE: '有效', EXPIRED: '已到期', NOT_YET_VALID: '未生效', REVOKED: '已撤回' } as Record<string, string>)[query.data.state] ?? query.data.state}</Tag><Tag>节点修订 {query.data.loadedRevision} / {query.data.targetRevision}</Tag><Button size="small" loading={query.isFetching} onClick={() => void query.refetch()}>刷新配置与状态</Button></Space>}
    <Form form={form} layout="vertical" autoComplete="off" disabled={!query.data} onFinish={(values) => save.mutate(values)}>
      <Form.Item name="validFrom" label="生效时间（留空立即生效）"><Input type="datetime-local" step="0.001" /></Form.Item>
      <Form.Item name="expiresAt" label="到期时间（留空不自动到期）" dependencies={['validFrom']} rules={[{ validator: async (_, value) => { const from = form.getFieldValue('validFrom'); if (value && from && Date.parse(value) <= Date.parse(from)) throw new Error('到期时间必须晚于生效时间'); } }]}><Input type="datetime-local" step="0.001" /></Form.Item>
      {target?.kind === 'subscriptions' && <Form.Item name="requestsPerSecond" label="此订阅单节点每秒请求数（零不额外限制）"><InputNumber min={0} max={1000000} precision={0} /></Form.Item>}
    </Form>
  </Drawer>;
};
