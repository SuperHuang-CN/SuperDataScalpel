import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { InfoCircleOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Form, Input, Space, Tag, Typography, message } from 'antd';
import { useEffect, useMemo } from 'react';
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
  const persistedAllowCidrs = policy?.allowCidrs.join('\n') ?? '';
  const persistedDenyCidrs = policy?.denyCidrs.join('\n') ?? '';
  const persistedValues = useMemo<PolicyFormValues>(() => ({
    allowCidrs: persistedAllowCidrs,
    denyCidrs: persistedDenyCidrs,
  }), [persistedAllowCidrs, persistedDenyCidrs]);
  const watchedAllowCidrs = Form.useWatch('allowCidrs', form) ?? '';
  const watchedDenyCidrs = Form.useWatch('denyCidrs', form) ?? '';
  const dirty = (
    watchedAllowCidrs !== persistedValues.allowCidrs || watchedDenyCidrs !== persistedValues.denyCidrs
  );

  useEffect(() => {
    form.setFieldsValue(persistedValues);
  }, [form, persistedValues]);

  const save = async (values: PolicyFormValues) => {
    const nextSavedValues = {
      allowCidrs: parseRules(values.allowCidrs).join('\n'),
      denyCidrs: parseRules(values.denyCidrs).join('\n'),
    };
    try {
      await updateMutation.mutateAsync({
        id: engine.id,
        request: { allowCidrs: parseRules(nextSavedValues.allowCidrs), denyCidrs: parseRules(nextSavedValues.denyCidrs) },
      });
      form.setFieldsValue(nextSavedValues);
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
        <div className="service-engine-policy-summary">
          <div className="service-engine-policy-summary-item">
            <span>策略状态</span>
            <Tag color={statusColors[policy.status]}>{statusLabels[policy.status]}</Tag>
          </div>
          <div className="service-engine-policy-summary-item">
            <span>最后同步</span>
            <ManagementDateTime value={policy.appliedAt} />
          </div>
          <div className="service-engine-policy-summary-item">
            <span>同步版本</span>
            <strong>已应用 v{policy.appliedRevision}</strong>
            <Typography.Text type="secondary">期望 v{policy.desiredRevision}</Typography.Text>
          </div>
          {canUpdate && (
            <div className="service-engine-policy-summary-actions">
              <Button
                icon={<SyncOutlined />}
                disabled={dirty}
                loading={syncMutation.isPending}
                onClick={() => void sync()}
              >
                同步已保存配置
              </Button>
            </div>
          )}
        </div>
      )}
      {policy?.lastError && <Alert className="service-engine-policy-error" type="error" showIcon message="最近同步失败" description={policy.lastError} />}
      <div className="service-engine-policy-hint">
        <InfoCircleOutlined aria-hidden />
        <span>仅已发布的业务服务受此策略保护；通过网关访问时，请将网关出口 IP 或 CIDR 加入白名单。</span>
      </div>
      <Form<PolicyFormValues> className="service-engine-policy-form" form={form} layout="vertical" autoComplete="off" onFinish={(values) => void save(values)}>
        <div className="service-engine-policy-rule-grid">
          <Form.Item
            label={<Space size={6}><span>白名单</span><Typography.Text type="danger">必填</Typography.Text></Space>}
            name="allowCidrs"
            rules={[{ required: true, whitespace: true, message: '至少配置一条白名单 IP 或 CIDR' }]}
            extra="每行一条，支持 IPv4、IPv6、单 IP 或 CIDR。"
          >
            <Input.TextArea rows={8} placeholder={'127.0.0.1/32\n::1/128'} disabled={!canUpdate} />
          </Form.Item>
          <Form.Item label="黑名单" name="denyCidrs" extra="每行一条；黑名单优先于白名单。">
            <Input.TextArea rows={8} placeholder="例如：10.0.0.99/32" disabled={!canUpdate} />
          </Form.Item>
        </div>
        {canUpdate && (
          <div className="service-engine-policy-actions">
            {dirty && <Typography.Text type="warning">有未保存修改</Typography.Text>}
            <Space>
              {dirty && <Button onClick={() => form.setFieldsValue(persistedValues)}>撤销修改</Button>}
              <Button type="primary" disabled={!dirty} loading={updateMutation.isPending} onClick={() => form.submit()}>保存并同步</Button>
            </Space>
          </div>
        )}
      </Form>
    </section>
  );
};
