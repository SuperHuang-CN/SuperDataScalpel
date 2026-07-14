import { Button, Descriptions, Drawer, Form, Input, InputNumber, Switch, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useUpdateSystemConfiguration } from '../hooks/useSystemConfigurations';
import type { SystemConfiguration, UpdateSystemConfigurationRequest } from '../model/systemConfiguration';

interface SystemConfigurationDrawerProps {
  configuration: SystemConfiguration | null;
  open: boolean;
  onClose: () => void;
}

export const SystemConfigurationDrawer = ({
  configuration,
  open,
  onClose,
}: SystemConfigurationDrawerProps) => {
  const [form] = Form.useForm<UpdateSystemConfigurationRequest>();
  const [messageApi, messageContext] = message.useMessage();
  const updateMutation = useUpdateSystemConfiguration();

  useEffect(() => {
    if (configuration && open) {
      form.setFieldsValue({ configValue: configuration.configValue });
      form.setFields([{ name: 'configValue', errors: [] }]);
    }
  }, [configuration, form, open]);

  const submit = async (values: UpdateSystemConfigurationRequest) => {
    if (!configuration) return;

    try {
      await updateMutation.mutateAsync({ id: configuration.id, request: values });
      messageApi.success('系统配置已保存');
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存系统配置失败');
    }
  };

  const valueEditor = () => {
    if (!configuration) return null;

    switch (configuration.valueType) {
      case 'INTEGER':
        return <InputNumber stringMode className="configuration-value-editor" />;
      case 'BOOLEAN':
        return <Switch checkedChildren="是" unCheckedChildren="否" />;
      case 'STRING':
        return <Input maxLength={4000} />;
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title="修改系统配置"
        open={open}
        size="default"
        onClose={onClose}
        destroyOnHidden
        footer={(
          <Button type="primary" loading={updateMutation.isPending} onClick={() => form.submit()}>
            保存
          </Button>
        )}
      >
        {configuration && (
          <Form form={form} layout="vertical" onFinish={submit}>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="名称">{configuration.name}</Descriptions.Item>
              <Descriptions.Item label="配置键">{configuration.configKey}</Descriptions.Item>
              <Descriptions.Item label="类型">{configuration.valueType}</Descriptions.Item>
              <Descriptions.Item label="说明">{configuration.description || '—'}</Descriptions.Item>
            </Descriptions>
            <Form.Item
              label="配置值"
              name="configValue"
              className="configuration-value-form-item"
              rules={[{ required: true, whitespace: true, message: '配置值不能为空' }]}
              getValueFromEvent={configuration.valueType === 'BOOLEAN'
                ? (checked: boolean) => String(checked)
                : undefined}
              getValueProps={configuration.valueType === 'BOOLEAN'
                ? (value: string | undefined) => ({ checked: value === 'true' })
                : undefined}
            >
              {valueEditor()}
            </Form.Item>
          </Form>
        )}
      </Drawer>
    </>
  );
};
