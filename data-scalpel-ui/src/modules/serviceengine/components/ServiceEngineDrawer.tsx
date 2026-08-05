import { Alert, Button, Col, Drawer, Form, Input, Row, Space, Switch, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useCreateServiceEngine,
  useTestNewServiceEngine,
  useTestServiceEngine,
  useUpdateServiceEngine,
} from '../hooks/useServiceEngines';
import type {
  CreateServiceEngineRequest,
  ServiceEngine,
  ServiceEngineTestResult,
  UpdateServiceEngineRequest,
} from '../model/serviceEngine';

interface ServiceEngineDrawerProps {
  open: boolean;
  engine: ServiceEngine | null;
  canTest: boolean;
  onClose: () => void;
}

interface ServiceEngineFormValues {
  name: string;
  adminUrl: string;
  publicUrl: string;
  managementToken?: string;
  enabled: boolean;
  description?: string;
}

const normalizedOptionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

export const ServiceEngineDrawer = ({ open, engine, canTest, onClose }: ServiceEngineDrawerProps) => {
  const [form] = Form.useForm<ServiceEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [testedConnection, setTestedConnection] = useState<{
    fingerprint: string;
    result: ServiceEngineTestResult;
  } | null>(null);
  const editing = Boolean(engine);
  const createMutation = useCreateServiceEngine();
  const updateMutation = useUpdateServiceEngine();
  const testNewMutation = useTestNewServiceEngine();
  const testStoredMutation = useTestServiceEngine();
  const watchedAdminUrl = Form.useWatch('adminUrl', form);
  const watchedManagementToken = Form.useWatch('managementToken', form);
  const testFingerprint = JSON.stringify([
    engine?.id ?? null,
    editing ? engine?.code : null,
    watchedAdminUrl?.trim(),
    watchedManagementToken?.trim(),
  ]);
  const testResult = testedConnection?.fingerprint === testFingerprint
    ? testedConnection.result
    : null;

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (engine) {
      form.setFieldsValue({
        name: engine.name,
        adminUrl: engine.adminUrl,
        publicUrl: engine.publicUrl,
        managementToken: undefined,
        enabled: engine.enabled,
        description: engine.description ?? undefined,
      });
      return;
    }
    form.setFieldsValue({ enabled: true });
  }, [engine, form, open]);

  const close = () => {
    form.resetFields();
    setTestedConnection(null);
    onClose();
  };

  const submit = async (values: ServiceEngineFormValues) => {
    const request: UpdateServiceEngineRequest = {
      name: values.name.trim(),
      adminUrl: values.adminUrl.trim(),
      publicUrl: values.publicUrl.trim(),
      managementToken: normalizedOptionalText(values.managementToken),
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
          managementToken: values.managementToken?.trim() ?? '',
        } satisfies CreateServiceEngineRequest);
        messageApi.success('Service Engine 已创建');
      }
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 Service Engine 失败');
    }
  };

  const testConnection = async () => {
    try {
      const fields: (keyof ServiceEngineFormValues)[] = ['adminUrl', 'managementToken'];
      const values = await form.validateFields(fields);
      const result = engine
        ? await testStoredMutation.mutateAsync({
          id: engine.id,
          request: {
            adminUrl: values.adminUrl.trim(),
            managementToken: normalizedOptionalText(values.managementToken),
          },
        })
        : await testNewMutation.mutateAsync({
          adminUrl: values.adminUrl.trim(),
          managementToken: values.managementToken?.trim() ?? '',
        });
      setTestedConnection({ fingerprint: testFingerprint, result });
      messageApi.success('Service Engine 连接成功');
    } catch (error) {
      setTestedConnection(null);
      if (error instanceof ApiError || error instanceof Error) {
        messageApi.error(error.message);
      }
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
            {canTest && (
              <Button
                loading={testNewMutation.isPending || testStoredMutation.isPending}
                onClick={() => void testConnection()}
              >
                测试连接
              </Button>
            )}
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>
              {editing ? '保存' : '创建'}
            </Button>
          </Space>
        )}
      >
        <Form<ServiceEngineFormValues> autoComplete="off" form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Row gutter={12}>
            <Col span={24}>
              <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                <Input autoFocus />
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
            <Col span={24}>
              <Form.Item
                label={editing ? 'Management Token（留空保持不变）' : 'Management Token'}
                name="managementToken"
                extra={editing && engine?.managementTokenConfigured ? '当前已配置 Management Token。' : undefined}
                rules={editing
                  ? [{ max: 1000, message: 'Management Token 不能超过 1000 个字符' }]
                  : [
                    { required: true, whitespace: true, message: '请输入 Management Token' },
                    { max: 1000, message: 'Management Token 不能超过 1000 个字符' },
                  ]}
              >
                <Input.Password name="service-engine-management-token" autoComplete="off" />
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
          {testResult && (
            <Alert
              type="success"
              showIcon
              message="连接测试成功"
              description={(
                <Space direction="vertical" size={2}>
                  <Typography.Text>Engine Code：<Typography.Text code>{testResult.code}</Typography.Text></Typography.Text>
                  <Typography.Text>响应时间：{testResult.elapsedMs} ms</Typography.Text>
                  <Typography.Text>支持数据库：{testResult.databaseTypes.join('、') || '无'}</Typography.Text>
                </Space>
              )}
            />
          )}
        </Form>
      </Drawer>
    </>
  );
};
