import { Form, Input, Modal, Select } from 'antd';
import { useEffect } from 'react';
import {
  dataServiceAccessModeLabels,
  type GatewayServiceBinding,
  type PublishDataServiceRequest,
} from '../model/dataService';

interface PublishDataServiceModalProps {
  service: { code: string; name: string; gatewayBindings: GatewayServiceBinding[] } | null;
  loading?: boolean;
  onCancel: () => void;
  onPublish: (request: PublishDataServiceRequest) => void | Promise<void>;
}

interface PublishFormValues extends PublishDataServiceRequest {
}

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
      gatewayRoutePath: existing?.gatewayRoutePath ?? `/open-api/v1/${service.code}`,
      accessMode: existing?.accessMode ?? 'PUBLIC',
    });
  }, [form, service]);

  return (
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      title={service ? `发布“${service.name}”到网关` : '发布到网关'}
      open={Boolean(service)}
      okText="发布"
      cancelText="取消"
      confirmLoading={loading}
      onCancel={onCancel}
      onOk={() => form.submit()}
      destroyOnHidden
    >
      <Form<PublishFormValues> form={form} layout="vertical" autoComplete="off" onFinish={onPublish}>
        <Form.Item
          label="网关公开路径"
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
          <Input placeholder="如：/open-api/v1/customers" />
        </Form.Item>
        <Form.Item label="访问方式" name="accessMode" rules={[{ required: true, message: '请选择访问方式' }]}>
          <Select options={Object.entries(dataServiceAccessModeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
