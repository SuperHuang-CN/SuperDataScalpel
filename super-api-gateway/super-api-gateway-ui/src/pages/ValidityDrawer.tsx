import { useEffect } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Button, Drawer, Form, Input, InputNumber, Space, Tag, message } from 'antd';
import { api, post } from '../api';

export interface ValidityTarget { kind: 'keys' | 'subscriptions'; id: string; source: string; name: string }
interface Validity { validFrom: string | null; expiresAt: string | null; requestsPerSecond: number; state: string; targetRevision: number; loadedRevision: number }
type Values = { validFrom: string; expiresAt: string; requestsPerSecond: number };
const localTime = (value: string | null) => value ? new Date(Date.parse(value) - new Date(value).getTimezoneOffset() * 60000).toISOString().slice(0, -1) : '';
export const ValidityDrawer = ({ target, onClose }: { target?: ValidityTarget; onClose: () => void }) => {
  const [form] = Form.useForm<Values>();
  const query = useQuery({ queryKey: ['validity', target?.kind, target?.id], queryFn: () => api<Validity>(`/access-validities/${target!.kind}/${target!.id}`), enabled: !!target });
  const managed = target?.source === 'DATASCALPEL';
  useEffect(() => { if (query.data) form.setFieldsValue({ validFrom: localTime(query.data.validFrom), expiresAt: localTime(query.data.expiresAt), requestsPerSecond: query.data.requestsPerSecond }); }, [query.data, form]);
  const save = useMutation({ mutationFn: (values: Values) => post(`/access-validities/${target!.kind}/${target!.id}/actions/update`, { validFrom: values.validFrom ? new Date(values.validFrom).toISOString() : null, expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : null, requestsPerSecond: values.requestsPerSecond ?? 0 }),
    onSuccess: () => { message.success('已保存，请核对加载修订'); void query.refetch(); }, onError: (error: Error) => message.error(error.message) });
  return <Drawer title={`${target?.name ?? ''} · 有效期`} size={560} open={!!target} onClose={onClose} footer={<Space><Button onClick={onClose}>关闭</Button>{!managed && <Button type="primary" disabled={!query.data} loading={save.isPending} onClick={() => form.submit()}>保存</Button>}</Space>}>
    <Alert type="info" title={managed ? 'DataScalpel 托管对象只读，请在 Admin 修改。' : '时间显示为本地时区。留空不限制；到期自动拒绝，续期不会恢复已撤回对象。'} />
    {query.error && <Alert type="error" title={query.error.message} />}
    {query.data && <Space><Tag>{query.data.state} · 修订 {query.data.loadedRevision} / {query.data.targetRevision}</Tag><Button size="small" loading={query.isFetching} onClick={() => void query.refetch()}>刷新配置与状态</Button></Space>}
    <Form form={form} layout="vertical" autoComplete="off" disabled={managed || !query.data} onFinish={(values) => save.mutate(values)}>
      <Form.Item name="validFrom" label="生效时间"><Input type="datetime-local" step="0.001" /></Form.Item>
      <Form.Item name="expiresAt" label="到期时间"><Input type="datetime-local" step="0.001" /></Form.Item>
      {target?.kind === 'subscriptions' && <Form.Item name="requestsPerSecond" label="订阅单节点每秒请求数（零不限）"><InputNumber min={0} max={1000000} precision={0} /></Form.Item>}
    </Form>
  </Drawer>;
};
