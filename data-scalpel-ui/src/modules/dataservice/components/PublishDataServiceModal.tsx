import { ApiOutlined, CloudUploadOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Tag, Typography } from 'antd';
import { useEffect } from 'react';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  dataServiceAccessModeLabels,
  type GatewayServiceBinding,
  type PublishDataServiceRequest,
} from '../model/dataService';

interface PublishDataServiceModalProps {
  service: { code: string; name: string; contextPath: string | null; gatewayBindings: GatewayServiceBinding[] } | null;
  loading?: boolean;
  onCancel: () => void;
  onPublish: (request: PublishDataServiceRequest) => void | Promise<void>;
}

type PublishFormValues = PublishDataServiceRequest;

export const PublishDataServiceModal = ({
  service,
  loading = false,
  onCancel,
  onPublish,
}: PublishDataServiceModalProps) => {
  const [form] = Form.useForm<PublishFormValues>();

  useEffect(() => {
    if (!service) return;
    const existing = service.gatewayBindings[0];
    form.setFieldsValue({
      gatewayRoutePath: existing?.gatewayRoutePath ?? service.contextPath ?? '',
      accessMode: existing?.accessMode ?? 'PUBLIC',
    });
  }, [form, service]);

  return (
    <Modal
      rootClassName="business-overlay business-modal-overlay publish-data-service-modal"
      title={(
        <div className="publish-data-service-title">
          <span className="publish-data-service-title-icon" aria-hidden="true"><CloudUploadOutlined /></span>
          <span className="publish-data-service-title-copy">
            <span>发布数据服务到网关</span>
            <Typography.Text type="secondary">配置稳定公开路径与调用方访问模式</Typography.Text>
          </span>
        </div>
      )}
      open={Boolean(service)}
      closable={!loading}
      maskClosable={!loading}
      onCancel={onCancel}
      footer={(
        <div className="publish-data-service-footer">
          <InlineFeedback tone="info" label={service ? `发布 ${service.name} · ${service.code}` : '等待选择数据服务'} />
          <Space>
            <Button disabled={loading} onClick={onCancel}>取消</Button>
            <Button type="primary" loading={loading} onClick={() => form.submit()}>确认发布</Button>
          </Space>
        </div>
      )}
      destroyOnHidden
    >
      <div className="publish-data-service-context">
        <span className="publish-data-service-context-icon" aria-hidden="true"><ApiOutlined /></span>
        <span>
          <strong>{service?.name ?? '—'}</strong>
          <Typography.Text type="secondary">{service?.code ?? '—'}</Typography.Text>
        </span>
        <Tag>{service?.gatewayBindings.length ? '更新发布配置' : '首次发布'}</Tag>
      </div>
      <Form<PublishFormValues> form={form} layout="vertical" autoComplete="off" className="publish-data-service-form" onFinish={onPublish}>
        <Form.Item
          label={(
            <span className="publish-data-service-field-label">
              网关公开路径
              <ContextHelp ariaLabel="查看网关公开路径规则" content="路径必须位于 /open-api/v1/，使用小写字母、数字、斜杠、下划线或连字符；不能使用动态参数、连续斜杠或以斜杠结尾。" presentation="popover" placement="bottomLeft" />
            </span>
          )}
          name="gatewayRoutePath"
          rules={[
            { required: true, whitespace: true, message: '请输入网关公开路径' },
            {
              validator: async (_, value: string | undefined) => {
                const path = value?.trim().toLowerCase() ?? '';
                if (!/^\/open-api\/v1\/[a-z0-9][a-z0-9/_-]*$/.test(path) || path.endsWith('/') || path.includes('//')) {
                  throw new Error('路径必须位于 /open-api/v1/，使用小写静态路径且不能以 / 结尾');
                }
              },
            },
          ]}
        >
          <Input name="data-service-gateway-route-path" autoComplete="off" prefix={<ApiOutlined />} placeholder="如：/open-api/v1/customers" />
        </Form.Item>
        <Form.Item label="访问方式" name="accessMode" rules={[{ required: true, message: '请选择访问方式' }]}>
          <Select options={Object.entries(dataServiceAccessModeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
