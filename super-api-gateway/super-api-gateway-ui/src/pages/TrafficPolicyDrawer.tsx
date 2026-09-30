import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Drawer, Form, Input, InputNumber, Space, Tag, message } from 'antd';
import { api, post } from '../api';
import type { Service } from '../model';

interface Policy { requestsPerSecond: number; consumerRequestsPerSecond: number; maxConcurrentRequests: number; maxRequestBytes: number; allowedCidrs: string[]; deniedCidrs: string[] }
interface State { policy: Policy; targetRevision: number; loadedRevision: number; scope: string }
type Values = Omit<Policy, 'allowedCidrs' | 'deniedCidrs'> & { allowedCidrs: string; deniedCidrs: string };
const lines = (value: string) => value.split(/[\n,]+/).map((v) => v.trim()).filter(Boolean);
export const TrafficPolicyDrawer = ({ service, onClose }: { service?: Service; onClose: () => void }) => {
  const [form] = Form.useForm<Values>();
  const cache = useQueryClient();
  const query = useQuery({ queryKey: ['traffic-policy', service?.id], queryFn: () => api<State>(`/services/${service!.id}/traffic-policy`), enabled: !!service });
  const managed = service?.source === 'DATASCALPEL';
  useEffect(() => {
    if (query.data) form.setFieldsValue({ ...query.data.policy, allowedCidrs: query.data.policy.allowedCidrs.join('\n'), deniedCidrs: query.data.policy.deniedCidrs.join('\n') });
  }, [query.data, form]);
  const save = useMutation({
    mutationFn: (values: Values) => post<State>(`/services/${service!.id}/actions/update-traffic-policy`, { ...values, allowedCidrs: lines(values.allowedCidrs), deniedCidrs: lines(values.deniedCidrs) }),
    onSuccess: () => { message.success('策略已保存，请在运行实例中核对加载修订'); cache.invalidateQueries({ queryKey: ['traffic-policy'] }); onClose(); },
    onError: (error: Error) => message.error(error.message),
  });
  return <Drawer title={`${service?.name ?? ''} · 保护策略`} size={640} open={!!service} onClose={onClose}
    footer={<Space><Button onClick={onClose}>关闭</Button>{!managed && <Button type="primary" disabled={!query.data} loading={save.isPending} onClick={() => form.submit()}>保存</Button>}</Space>}>
    {query.error && <Alert type="error" title={query.error.message} />}
    <Alert type="info" title={managed ? 'DataScalpel 托管：只读，请在 Admin 的服务详情中修改。' : '单节点策略，零表示不限；不信任转发头，黑名单优先。'} />
    {query.data && <Tag>已加载 / 目标修订 {query.data.loadedRevision} / {query.data.targetRevision}</Tag>}
    <Form form={form} layout="vertical" autoComplete="off" disabled={managed || !query.data} onFinish={(values) => save.mutate(values)}>
      <Form.Item name="requestsPerSecond" label="服务每秒请求数" rules={[{ required: true }]}><InputNumber min={0} max={1000000} precision={0} /></Form.Item>
      <Form.Item name="consumerRequestsPerSecond" label="每消费者每秒请求数" rules={[{ required: true }]}><InputNumber min={0} max={1000000} precision={0} /></Form.Item>
      <Form.Item name="maxConcurrentRequests" label="服务并发上限" rules={[{ required: true }]}><InputNumber min={0} max={100000} precision={0} /></Form.Item>
      <Form.Item name="maxRequestBytes" label="请求体上限（字节）" rules={[{ required: true }]}><InputNumber min={0} max={1073741824} precision={0} /></Form.Item>
      <Form.Item name="allowedCidrs" label="允许 IP / CIDR（每行一条）"><Input.TextArea rows={3} /></Form.Item>
      <Form.Item name="deniedCidrs" label="拒绝 IP / CIDR（每行一条）"><Input.TextArea rows={3} /></Form.Item>
    </Form>
  </Drawer>;
};
