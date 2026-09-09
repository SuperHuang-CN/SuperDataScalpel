import { Button, Drawer, Form, Input, Select, Space, TreeSelect, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createMetric, updateMetric } from '../api/metricApi';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { invalidateMetrics } from '../hooks/useMetrics';
import { metricKindLabels, type Metric, type MetricBasics } from '../model/metric';
import { useCurrentUser } from '../../system';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
export const MetricBasicsDrawer = ({ metric, directoryId, onClose, onCreated }: { metric?: Metric; directoryId?: string | null; onClose: () => void; onCreated: (metric: Metric) => void }) => {
 const [form] = Form.useForm<MetricBasics & { code: string }>(); const client = useQueryClient(); const user = useCurrentUser(); const canViewDirectories = user.data?.permissions.includes('directory.view') ?? false; const directories = useDirectoryTree('METRIC', canViewDirectories); const [msg, context] = message.useMessage();
 const mutation = useMutation({ mutationFn: (value: MetricBasics & { code: string }) => metric ? updateMetric(metric.id, value) : createMetric(value), onSuccess: async result => { await invalidateMetrics(client); void msg.success('指标基础资料已保存'); onCreated(result); } });
 return <Drawer open title={metric ? '修改指标资料' : '新建指标'} width={720} rootClassName="business-overlay business-drawer-overlay" onClose={onClose} footer={<Space className="metric-drawer-actions"><Button onClick={onClose}>取消</Button><Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>{metric ? '保存' : '创建并配置口径'}</Button></Space>}>
  {context}<Form form={form} layout="vertical" autoComplete="off" initialValues={metric ?? { kind: 'DERIVED', directoryId: directoryId ?? null }} onFinish={value => mutation.mutate(value)}><div className="metric-form-grid">
   <Form.Item label="指标名称" name="name" rules={[{ required: true, whitespace: true, max: 100 }]}><Input maxLength={100} /></Form.Item>
   <Form.Item label="指标编码" name="code" rules={[{ required: true, pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '小写字母开头，仅含小写字母、数字和下划线，最长 64 位' }]}><Input disabled={Boolean(metric)} maxLength={64} /></Form.Item>
   <Form.Item label="指标类型" name="kind" rules={[{ required: true }]}><Select disabled={Boolean(metric?.publishedVersion)} options={Object.entries(metricKindLabels).map(([value,label]) => ({ value,label }))} /></Form.Item>
   <Form.Item label="所属目录" name="directoryId"><TreeSelect disabled={!canViewDirectories} allowClear treeData={directoryTreeSelectData(directories.data ?? [])} loading={directories.isFetching} /></Form.Item>
   <Form.Item label="业务负责人" name="ownerName" required extra="发布前必填，可先留空保存；填写负责解释口径的人员或部门，无需平台账号。"><Input placeholder="例如：水文监测科 / 张三" maxLength={100} /></Form.Item>
   <Form.Item className="metric-span" label="简介" name="summary"><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
  </div></Form>{mutation.isError && <InlineFeedback tone="error" label={mutation.error.message} />}
 </Drawer>;
};
