import { ControlOutlined, SettingOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, InputNumber, Row, Space, Switch, Tag, Typography, message } from 'antd';
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
        return <InputNumber stringMode name="system-configuration-integer-value" autoComplete="off" className="configuration-value-editor" />;
      case 'BOOLEAN':
        return <Switch checkedChildren="是" unCheckedChildren="否" />;
      case 'STRING':
        return <Input name="system-configuration-string-value" autoComplete="off" maxLength={4000} placeholder="输入配置值" />;
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer system-configuration-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><SettingOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>修改系统配置</span>
              <Typography.Text type="secondary">调整平台运行参数，配置标识与类型保持不变</Typography.Text>
            </span>
          </div>
        )}
        extra={configuration && <Tag className="data-model-drawer-header-tag">{configuration.valueType}</Tag>}
        open={open}
        size={680}
        onClose={onClose}
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge status="processing" text={configuration ? configuration.configKey : '等待选择配置'} />
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button type="primary" loading={updateMutation.isPending} onClick={() => form.submit()}>保存配置</Button>
            </Space>
          </div>
        )}
      >
        {configuration && (
          <Form
            name="system-configuration-editor-form"
            className="data-model-form system-configuration-form"
            autoComplete="off"
            form={form}
            layout="vertical"
            onFinish={(values) => void submit(values)}
          >
            <section className="data-model-form-section">
              <header className="data-model-form-section-header">
                <span className="data-model-form-section-icon" aria-hidden="true"><ControlOutlined /></span>
                <span className="data-model-form-section-copy">
                  <span className="data-model-form-section-title">配置内容</span>
                  <Typography.Text type="secondary">核对配置语义后修改当前生效值</Typography.Text>
                </span>
              </header>
              <div className="data-model-form-section-body">
                <div className="system-configuration-context-grid">
                  <div><span>配置项</span><strong>{configuration.name}</strong></div>
                  <div><span>配置键</span><code>{configuration.configKey}</code></div>
                  <div className="system-configuration-context-description">
                    <span>用途说明</span>
                    <p>{configuration.description || '暂无说明'}</p>
                  </div>
                </div>
                <Row gutter={14}>
                  <Col span={24}>
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
                  </Col>
                </Row>
              </div>
            </section>
          </Form>
        )}
      </Drawer>
    </>
  );
};
