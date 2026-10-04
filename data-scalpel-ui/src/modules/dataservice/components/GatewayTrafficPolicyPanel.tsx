import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { SafetyOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, InputNumber, Space, Tag, Typography, message } from 'antd';
import { CompactAlert } from '../../../shared/components/ContextualFeedback';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { getGatewayTrafficPolicy, updateGatewayTrafficPolicy, type GatewayTrafficPolicy } from '../api/gatewayTrafficPolicyApi';
import { useGatewayAccessRecent } from '../hooks/useGatewayAccess';

type PolicyForm = Omit<GatewayTrafficPolicy, 'allowedCidrs' | 'deniedCidrs'> & { allowedCidrs: string; deniedCidrs: string };
const lines = (value: string) => value.split(/[\n,]+/).map((line) => line.trim()).filter(Boolean);

export const GatewayTrafficPolicyPanel = ({ serviceId, canPublish, supported }: { serviceId: string; canPublish: boolean; supported: boolean }) => {
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<PolicyForm>();
  const cache = useQueryClient();
  const key = ['gateway-traffic-policy', serviceId];
  const query = useQuery({ queryKey: key, queryFn: () => getGatewayTrafficPolicy(serviceId), enabled: supported, refetchInterval: open ? false : 15_000 });
  const recent = useGatewayAccessRecent({ dataServiceId: serviceId });
  const mutation = useMutation({
    mutationFn: (values: PolicyForm) => updateGatewayTrafficPolicy(serviceId, { ...values, allowedCidrs: lines(values.allowedCidrs), deniedCidrs: lines(values.deniedCidrs) }),
    onSuccess: (saved) => { cache.setQueryData(key, saved); setOpen(false); message.success('策略已保存，请核对节点加载修订'); },
    onError: (error: Error) => message.error(error.message),
  });
  const edit = () => {
    const policy = query.data?.policy;
    if (!policy) return;
    form.setFieldsValue({ ...policy, allowedCidrs: policy.allowedCidrs.join('\n'), deniedCidrs: policy.deniedCidrs.join('\n') });
    setOpen(true);
  };
  const policy = query.data?.policy;
  return <div className="data-service-detail-tab-panel">
    <BusinessDetailSection title="网关保护策略" description="仅作用于网关调用，不修改服务定义。单节点令牌桶限流，零表示不限制。" icon={<SafetyOutlined />}
      extra={<Space><Button onClick={() => void query.refetch()} disabled={!supported}>刷新</Button><Button type="primary" disabled={!canPublish || !policy} onClick={edit}>配置</Button></Space>}>
      {!supported && <CompactAlert type="info" message="当前服务尚未接入自研网关，或使用未适配此策略的网关；不会静默下发无效配置。" />}
      {query.error && <CompactAlert type="error" message={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
      {policy && <Space wrap size="large">
        <Typography.Text>服务限流 {policy.requestsPerSecond || '不限'} req/s</Typography.Text>
        <Typography.Text>消费者限流 {policy.consumerRequestsPerSecond || '不限'} req/s</Typography.Text>
        <Typography.Text>并发 {policy.maxConcurrentRequests || '不限'}</Typography.Text>
        <Typography.Text>请求体 {policy.maxRequestBytes ? `${policy.maxRequestBytes} 字节` : '不限'}</Typography.Text>
        <Typography.Text>白名单 / 黑名单 {policy.allowedCidrs.length} / {policy.deniedCidrs.length}</Typography.Text>
        <Tag color={query.data!.loadedRevision >= query.data!.targetRevision ? 'success' : 'processing'}>节点修订 {query.data!.loadedRevision} / {query.data!.targetRevision}</Tag>
      </Space>}
    </BusinessDetailSection>
    <BusinessDetailSection title="最近十五分钟调用" description="已接收原始日志，包含当前小时；更多趋势和明细见 API 调用统计。" icon={<SafetyOutlined />}>
      {recent.error && <CompactAlert type="error" message={recent.error.message} />}
      {recent.data && <Space wrap><Typography.Text>调用 {recent.data.requestCount} · 成功 {recent.data.successCount} · 拒绝 {recent.data.rejectedCount} · 5xx {recent.data.serverErrorCount}</Typography.Text><Tag color={recent.data.ingestionEnabled ? 'blue' : 'warning'}>{recent.data.ingestionEnabled ? '异步日志接收已启用' : '日志接收已关闭'}</Tag></Space>}
    </BusinessDetailSection>
    <Drawer title="网关保护策略" rootClassName="business-overlay business-drawer-overlay resource-workspace-overlay" size={680} open={open} onClose={() => setOpen(false)}
      footer={<Space style={{ display: 'flex', justifyContent: 'flex-end' }}><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>保存并下发</Button></Space>}>
      <Form form={form} layout="vertical" autoComplete="off" onFinish={(values) => mutation.mutate(values)}>
        <CompactAlert type="info" message="所有限额按网关节点独立计算；只识别真实 TCP 来源 IP，不信任 X-Forwarded-For。黑名单优先，错误配置可能阻断调用。" />
        <Form.Item label="服务每秒请求数" name="requestsPerSecond" rules={[{ required: true }]}><InputNumber min={0} max={1000000} precision={0} /></Form.Item>
        <Form.Item label="每消费者每秒请求数" name="consumerRequestsPerSecond" rules={[{ required: true }]}><InputNumber min={0} max={1000000} precision={0} /></Form.Item>
        <Form.Item label="服务并发上限" name="maxConcurrentRequests" rules={[{ required: true }]}><InputNumber min={0} max={100000} precision={0} /></Form.Item>
        <Form.Item label="请求体上限（字节）" name="maxRequestBytes" rules={[{ required: true }]}><InputNumber min={0} max={1073741824} precision={0} /></Form.Item>
        <Form.Item label="允许来源 IP / CIDR（每行一条，留空不限制）" name="allowedCidrs"><Input.TextArea rows={3} /></Form.Item>
        <Form.Item label="拒绝来源 IP / CIDR（每行一条）" name="deniedCidrs"><Input.TextArea rows={3} /></Form.Item>
      </Form>
    </Drawer>
  </div>;
};
