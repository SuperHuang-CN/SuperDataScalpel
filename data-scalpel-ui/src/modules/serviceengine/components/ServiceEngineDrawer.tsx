import { Button, Col, Drawer, Form, Input, Row, Space, Switch, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateServiceEngine, useUpdateServiceEngine } from '../hooks/useServiceEngines';
import type { CreateServiceEngineRequest, ServiceEngine, UpdateServiceEngineRequest } from '../model/serviceEngine';

interface ServiceEngineDrawerProps {
  open: boolean;
  engine: ServiceEngine | null;
  onClose: () => void;
}

interface ServiceEngineFormValues {
  code?: string;
  name: string;
  adminUrl: string;
  publicUrl: string;
  enabled: boolean;
  description?: string;
}

const normalizedOptionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

export const ServiceEngineDrawer = ({ open, engine, onClose }: ServiceEngineDrawerProps) => {
  const [form] = Form.useForm<ServiceEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const editing = Boolean(engine);
  const createMutation = useCreateServiceEngine();
  const updateMutation = useUpdateServiceEngine();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (engine) {
      form.setFieldsValue({
        name: engine.name,
        adminUrl: engine.adminUrl,
        publicUrl: engine.publicUrl,
        enabled: engine.enabled,
        description: engine.description ?? undefined,
      });
      return;
    }
    form.setFieldsValue({ enabled: true });
  }, [engine, form, open]);

  const close = () => {
    form.resetFields();
    onClose();
  };

  const submit = async (values: ServiceEngineFormValues) => {
    const request: UpdateServiceEngineRequest = {
      name: values.name.trim(),
      adminUrl: values.adminUrl.trim(),
      publicUrl: values.publicUrl.trim(),
      enabled: values.enabled,
      description: normalizedOptionalText(values.description),
    };
    try {
      if (engine) {
        await updateMutation.mutateAsync({ id: engine.id, request });
        messageApi.success('Service Engine 已保存');
      } else {
        await createMutation.mutateAsync({
          ...request,
          code: values.code?.trim().toLowerCase() ?? '',
        } satisfies CreateServiceEngineRequest);
        messageApi.success('Service Engine 已创建');
      }
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 Service Engine 失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改 Service Engine' : '新建 Service Engine'}
        open={open}
        size={640}
        onClose={close}
        destroyOnHidden
        footer={(
          <Space>
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>
              {editing ? '保存' : '创建'}
            </Button>
          </Space>
        )}
      >
        <Form<ServiceEngineFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Row gutter={12}>
            {!editing && (
              <Col span={12}>
                <Form.Item
                  label="Engine 编码"
                  name="code"
                  rules={[
                    { required: true, whitespace: true, message: '请输入 Engine 编码' },
                    { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '以字母开头，仅支持字母、数字和下划线，最长 64 位' },
                  ]}
                >
                  <Input autoFocus placeholder="如：dev_engine_01" />
                </Form.Item>
              </Col>
            )}
            <Col span={12}>
              <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                <Input autoFocus={editing} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="管理地址" name="adminUrl" extra="Admin 会通过该地址调用 Engine 的 /internal/v1/** 管理接口。" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500, message: '地址不能超过 500 个字符' }]}>
                <Input placeholder="如：http://engine.internal:8081" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="公共地址" name="publicUrl" extra="用于登记调用方访问的公开 API 根地址。" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500, message: '地址不能超过 500 个字符' }]}>
                <Input placeholder="如：https://api.example.internal" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="启用" name="enabled" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                <Input.TextArea rows={4} maxLength={1000} showCount />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
