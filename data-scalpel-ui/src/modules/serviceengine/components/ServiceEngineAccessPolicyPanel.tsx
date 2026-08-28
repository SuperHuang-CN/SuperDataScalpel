import { Alert, Button, Descriptions, Form, Input, Space, Tag, Typography, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementDateTime } from '../../../shared/components/ManagementListCells';
import {
  useServiceEngineAccessPolicy,
  useSyncServiceEngineAccessPolicy,
  useUpdateServiceEngineAccessPolicy,
} from '../hooks/useServiceEngines';
import type { ServiceEngine, ServiceEngineAccessPolicyStatus } from '../model/serviceEngine';

interface PolicyFormValues {
  allowCidrs: string;
  denyCidrs: string;
}

interface ServiceEngineAccessPolicyPanelProps {
  engine: ServiceEngine;
  canUpdate: boolean;
}

const statusColors: Record<ServiceEngineAccessPolicyStatus, string> = {
  NOT_CONFIGURED: 'default',
  PENDING: 'processing',
  READY: 'success',
  FAILED: 'error',
  OUTDATED: 'warning',
};

const statusLabels: Record<ServiceEngineAccessPolicyStatus, string> = {
  NOT_CONFIGURED: '未配置',
  PENDING: '同步中',
  READY: '已就绪',
  FAILED: '同步失败',
  OUTDATED: '待同步',
};

const parseRules = (value: string) => value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);

export const ServiceEngineAccessPolicyPanel = ({ engine, canUpdate }: ServiceEngineAccessPolicyPanelProps) => {
  const [form] = Form.useForm<PolicyFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const policyQuery = useServiceEngineAccessPolicy(engine.id);
  const updateMutation = useUpdateServiceEngineAccessPolicy();
  const syncMutation = useSyncServiceEngineAccessPolicy();
  const policy = policyQuery.data;

  useEffect(() => {
    form.setFieldsValue({
      allowCidrs: policy?.allowCidrs.join('\n') ?? '',
      denyCidrs: policy?.denyCidrs.join('\n') ?? '',
    });
  }, [form, policy]);

  const save = async (values: PolicyFormValues) => {
    try {
      await updateMutation.mutateAsync({
        id: engine.id,
        request: { allowCidrs: parseRules(values.allowCidrs), denyCidrs: parseRules(values.denyCidrs) },
      });
      messageApi.success('访问策略已同步到 Service Engine');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '访问策略同步失败');
    }
  };

  const sync = async () => {
    try {
      await syncMutation.mutateAsync(engine.id);
      messageApi.success('访问策略已重新同步');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '访问策略同步失败');
    }
  };

  return (
    <section className="service-engine-tab-panel service-engine-access-policy-panel">
      {messageContext}
      {policy && (
        <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="状态"><Tag color={statusColors[policy.status]}>{statusLabels[policy.status]}</Tag></Descriptions.Item>
          <Descriptions.Item label="同步版本">期望 {policy.desiredRevision} / 已应用 {policy.appliedRevision}</Descriptions.Item>
          <Descriptions.Item label="最后同步"><ManagementDateTime value={policy.appliedAt} /></Descriptions.Item>
          <Descriptions.Item label="最近更新"><ManagementDateTime value={policy.updatedAt} /></Descriptions.Item>
        </Descriptions>
      )}
      {policy?.lastError && <Alert type="error" showIcon message="最近同步失败" description={policy.lastError} />}
      <Alert type="info" showIcon message="仅保护已发布的业务服务" description="管理接口不受此策略影响。规则使用 Engine 看到的 TCP 对端 IP；通过网关访问时，请将网关出口 IP/CIDR 加入白名单。" />
      <Form<PolicyFormValues> form={form} layout="vertical" autoComplete="off" onFinish={(values) => void save(values)}>
        <Form.Item label="白名单" name="allowCidrs" rules={[{ required: true, whitespace: true, message: '至少配置一条白名单 IP 或 CIDR' }]} extra="每行一条，支持 IPv4、IPv6、单 IP 或 CIDR。">
          <Input.TextArea rows={8} placeholder={'127.0.0.1/32\n::1/128'} disabled={!canUpdate} />
        </Form.Item>
        <Form.Item label="黑名单" name="denyCidrs" extra="每行一条；黑名单优先于白名单。">
          <Input.TextArea rows={6} placeholder="例如：10.0.0.99/32" disabled={!canUpdate} />
        </Form.Item>
        <Typography.Text type="secondary">未命中白名单的直接请求会收到包含其来源 IP 的 403 提示，便于手工补充规则。</Typography.Text>
        <Space className="service-engine-access-policy-actions">
          <Button disabled={!policy || !canUpdate} loading={syncMutation.isPending} onClick={() => void sync()}>重新同步</Button>
          {canUpdate && <Button type="primary" loading={updateMutation.isPending} onClick={() => form.submit()}>保存并同步</Button>}
        </Space>
      </Form>
    </section>
  );
};
