import { CloudServerOutlined, GlobalOutlined, IdcardOutlined, KeyOutlined } from '@ant-design/icons';
import { Badge, Button, Drawer, Form, Input, Select, Space, Switch, Tag, Typography, message } from 'antd';
import { type ReactNode, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
  ServiceEngineType,
  UpdateServiceEngineRequest,
} from '../model/serviceEngine';

interface ServiceEngineDrawerProps {
  open: boolean;
  engine: ServiceEngine | null;
  canTest: boolean;
  onClose: () => void;
}

interface ServiceEngineFormValues {
  type: ServiceEngineType;
  code?: string;
  name: string;
  adminUrl: string;
  runtimeUrl: string;
  managementToken?: string;
  geoServerUsername?: string;
  geoServerPassword?: string;
  geoServerWorkspace?: string;
  enabled: boolean;
  description?: string;
}

const optionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

const FormSection = ({ title, description, icon, extra, help, children }: {
  title: string;
  description: string;
  icon: ReactNode;
  extra?: ReactNode;
  help?: ReactNode;
  children: ReactNode;
}) => (
  <section className="service-engine-form-section">
    <header className="service-engine-form-section-header">
      <span className="service-engine-form-section-icon" aria-hidden="true">{icon}</span>
      <span className="service-engine-form-section-copy">
        <span className="service-engine-form-section-title-row">
          <span className="service-engine-form-section-title">{title}</span>
          {help && <ContextHelp ariaLabel={`${title}说明`} content={help} presentation="popover" placement="bottomLeft" />}
        </span>
        <Typography.Text type="secondary">{description}</Typography.Text>
      </span>
      {extra && <span className="service-engine-form-section-extra">{extra}</span>}
    </header>
    <div className="service-engine-form-section-body">{children}</div>
  </section>
);

export const ServiceEngineDrawer = ({ open, engine, canTest, onClose }: ServiceEngineDrawerProps) => {
  const [form] = Form.useForm<ServiceEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [testedConnection, setTestedConnection] = useState<{ fingerprint: string; result: ServiceEngineTestResult } | null>(null);
  const editing = Boolean(engine);
  const createMutation = useCreateServiceEngine();
  const updateMutation = useUpdateServiceEngine();
  const testNewMutation = useTestNewServiceEngine();
  const testStoredMutation = useTestServiceEngine();
  const watchedType = Form.useWatch('type', form) ?? engine?.type ?? 'DATASCALPEL';
  const watchedAdminUrl = Form.useWatch('adminUrl', form);
  const watchedRuntimeUrl = Form.useWatch('runtimeUrl', form);
  const watchedManagementToken = Form.useWatch('managementToken', form);
  const watchedGeoServerUsername = Form.useWatch('geoServerUsername', form);
  const watchedGeoServerPassword = Form.useWatch('geoServerPassword', form);
  const watchedGeoServerWorkspace = Form.useWatch('geoServerWorkspace', form);
  const fingerprint = JSON.stringify([
    engine?.id ?? null, watchedType, watchedAdminUrl?.trim(), watchedRuntimeUrl?.trim(),
    watchedManagementToken?.trim(), watchedGeoServerUsername?.trim(),
    watchedGeoServerPassword?.trim(), watchedGeoServerWorkspace?.trim(),
  ]);
  const testResult = testedConnection?.fingerprint === fingerprint ? testedConnection.result : null;
  const geoServer = watchedType === 'GEOSERVER';

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (engine) {
      form.setFieldsValue({
        type: engine.type ?? 'DATASCALPEL',
        code: engine.code,
        name: engine.name,
        adminUrl: engine.adminUrl,
        runtimeUrl: engine.runtimeUrl,
        geoServerUsername: engine.geoServerUsername ?? undefined,
        geoServerWorkspace: engine.geoServerWorkspace ?? undefined,
        enabled: engine.enabled,
        description: engine.description ?? undefined,
      });
    } else {
      form.setFieldsValue({ type: 'DATASCALPEL', enabled: true, geoServerWorkspace: 'datascalpel' });
    }
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
      runtimeUrl: values.runtimeUrl.trim(),
      managementToken: values.type === 'DATASCALPEL' ? optionalText(values.managementToken) : undefined,
      geoServerUsername: values.type === 'GEOSERVER' ? optionalText(values.geoServerUsername) : undefined,
      geoServerPassword: values.type === 'GEOSERVER' ? optionalText(values.geoServerPassword) : undefined,
      geoServerWorkspace: values.type === 'GEOSERVER' ? optionalText(values.geoServerWorkspace) : undefined,
      enabled: values.enabled,
      description: optionalText(values.description),
    };
    try {
      if (engine) {
        await updateMutation.mutateAsync({ id: engine.id, request });
      } else {
        await createMutation.mutateAsync({
          ...request,
          type: values.type,
          code: values.type === 'GEOSERVER' ? values.code?.trim() : undefined,
        } satisfies CreateServiceEngineRequest);
      }
      messageApi.success(geoServer ? 'GeoServer 空间引擎已保存' : 'DataScalpel 服务引擎已保存');
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 Service Engine 失败');
    }
  };

  const testConnection = async () => {
    try {
      const fields: (keyof ServiceEngineFormValues)[] = geoServer
        ? ['type', 'code', 'adminUrl', 'runtimeUrl', 'geoServerUsername', 'geoServerPassword', 'geoServerWorkspace']
        : ['type', 'adminUrl', 'runtimeUrl', 'managementToken'];
      const values = await form.validateFields(fields);
      const request = {
        adminUrl: values.adminUrl.trim(),
        runtimeUrl: values.runtimeUrl.trim(),
        managementToken: optionalText(values.managementToken),
        geoServerUsername: optionalText(values.geoServerUsername),
        geoServerPassword: optionalText(values.geoServerPassword),
        geoServerWorkspace: optionalText(values.geoServerWorkspace),
      };
      const result = engine
        ? await testStoredMutation.mutateAsync({ id: engine.id, request })
        : await testNewMutation.mutateAsync({
          ...request,
          type: values.type,
          code: values.type === 'GEOSERVER' ? values.code?.trim() : undefined,
        });
      setTestedConnection({ fingerprint, result });
      messageApi.success(geoServer ? 'GeoServer 连接成功' : 'Service Engine 连接成功');
    } catch (error) {
      setTestedConnection(null);
      if (error instanceof ApiError || error instanceof Error) messageApi.error(error.message);
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="service-engine-drawer"
        title={<div className="service-engine-drawer-title">
          <span className="service-engine-drawer-title-icon" aria-hidden="true"><CloudServerOutlined /></span>
          <span className="service-engine-drawer-title-copy">
            <span>{editing ? '修改 Service Engine' : '新建 Service Engine'}</span>
            <Typography.Text type="secondary">配置普通服务引擎或 GeoServer 空间引擎</Typography.Text>
          </span>
        </div>}
        extra={<Tag className="service-engine-drawer-header-tag">{editing ? engine?.code : geoServer ? 'GeoServer' : 'DataScalpel'}</Tag>}
        open={open}
        size="min(760px, 100vw)"
        closable={{ placement: 'end' }}
        onClose={close}
        destroyOnHidden
        footer={<div className="service-engine-drawer-footer">
          {testResult ? <InlineFeedback
            tone="success"
            label="连接测试成功"
            ariaLabel="Service Engine 连接测试详情"
            detail={<div className="service-engine-test-result-detail">
              <span>Engine Code：<Typography.Text code>{testResult.code}</Typography.Text></span>
              {testResult.version && <span>版本：{testResult.version}</span>}
              <span>响应时间：{testResult.elapsedMs} ms</span>
              <span>支持数据库：{testResult.databaseTypes.join('、') || '无'}</span>
              {(testResult.capabilities?.length ?? 0) > 0 && <span>服务能力：{testResult.capabilities?.join('、')}</span>}
            </div>}
          /> : <Badge status="default" text={canTest ? '连接尚未测试' : '当前无连接测试权限'} />}
          <Space>
            {canTest && <Button loading={testNewMutation.isPending || testStoredMutation.isPending} onClick={() => void testConnection()}>测试连接</Button>}
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>{editing ? '保存修改' : '创建引擎'}</Button>
          </Space>
        </div>}
      >
        <Form<ServiceEngineFormValues> autoComplete="off" form={form} layout="vertical" className="service-engine-form" onFinish={(values) => void submit(values)}>
          <FormSection
            title="基本信息"
            description="引擎类型和 Code 创建后不可修改"
            icon={<IdcardOutlined />}
            extra={<span className="service-engine-enabled-control"><span>启用</span><Form.Item name="enabled" valuePropName="checked" noStyle><Switch aria-label="启用 Service Engine" /></Form.Item></span>}
          >
            <div className="service-engine-form-grid service-engine-form-grid-two-columns">
              <Form.Item label="引擎类型" name="type" rules={[{ required: true, message: '请选择引擎类型' }]}>
                <Select disabled={editing} options={[
                  { value: 'DATASCALPEL', label: 'DataScalpel 服务引擎' },
                  { value: 'GEOSERVER', label: 'GeoServer 空间引擎' },
                ]} />
              </Form.Item>
              {geoServer && <Form.Item label="Engine Code" name="code" rules={[
                { required: true, whitespace: true, message: '请输入 Engine Code' },
                { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '以字母开头，仅支持字母、数字和下划线' },
              ]}><Input name="geoserver-engine-code" autoComplete="off" disabled={editing} /></Form.Item>}
              <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100 }]}>
                <Input name="service-engine-display-name" autoComplete="off" autoFocus />
              </Form.Item>
              <Form.Item className="service-engine-form-grid-full" label="说明" name="description" rules={[{ max: 1000 }]}>
                <Input.TextArea name="service-engine-description" autoComplete="off" rows={3} maxLength={1000} showCount />
              </Form.Item>
            </div>
          </FormSection>

          <FormSection
            title="访问配置"
            description={geoServer ? '配置 GeoServer 管理与公开协议地址' : '分别配置管理面和运行面的内部访问地址'}
            icon={<GlobalOutlined />}
            help={<div className="service-engine-section-help">
              <span><strong>管理地址：</strong>{geoServer ? '可输入主机根地址或 GeoServer 根地址。' : 'Admin 调用 Engine 管理接口的地址。'}</span>
              <span><strong>运行地址：</strong>{geoServer ? '客户端访问 WMS/WFS 的地址。' : '网关转发到 Engine 服务路由的地址。'}</span>
            </div>}
          >
            <div className="service-engine-form-grid service-engine-form-grid-two-columns">
              <Form.Item label="管理地址" name="adminUrl" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500 }]}>
                <Input type="url" name="service-engine-admin-url" autoComplete="off" placeholder={geoServer ? '如：http://host:8080/geoserver' : '如：http://engine.internal:8081'} />
              </Form.Item>
              <Form.Item label="运行地址" name="runtimeUrl" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 500 }]}>
                <Input type="url" name="service-engine-runtime-url" autoComplete="off" placeholder={geoServer ? '如：http://host:8080/geoserver' : '如：http://engine.internal:8081'} />
              </Form.Item>
            </div>
          </FormSection>

          <FormSection title={geoServer ? 'GeoServer 配置' : '管理凭据'} description={geoServer ? '配置管理账号与专属 Workspace' : '用于管理接口认证，凭据将加密保存'} icon={<KeyOutlined />}>
            <div className="service-engine-form-grid service-engine-form-grid-two-columns">
              {!geoServer ? <Form.Item
                label={editing ? 'Management Token（留空保持不变）' : 'Management Token'}
                name="managementToken"
                extra={editing && engine?.managementTokenConfigured ? '当前已配置 Management Token。' : undefined}
                rules={editing ? [{ max: 1000 }] : [{ required: true, whitespace: true, message: '请输入 Management Token' }, { max: 1000 }]}
              ><BusinessSecretInput name="service-engine-management-token" autoComplete="off" /></Form.Item> : <>
                <Form.Item label="GeoServer 用户名" name="geoServerUsername" rules={[{ required: true, whitespace: true, message: '请输入 GeoServer 用户名' }, { max: 200 }]}>
                  <Input name="geoserver-management-account" autoComplete="off" />
                </Form.Item>
                <Form.Item
                  label={editing ? 'GeoServer 密码（留空保持不变）' : 'GeoServer 密码'}
                  name="geoServerPassword"
                  extra={editing && engine?.geoServerCredentialConfigured ? '当前已配置 GeoServer 密码。' : undefined}
                  rules={editing ? [{ max: 1000 }] : [{ required: true, whitespace: true, message: '请输入 GeoServer 密码' }, { max: 1000 }]}
                ><BusinessSecretInput name="geoserver-management-secret" autoComplete="off" /></Form.Item>
                <Form.Item label="Workspace" name="geoServerWorkspace" rules={[
                  { required: true, whitespace: true, message: '请输入 Workspace' },
                  { pattern: /^[a-z][a-z0-9_.-]{0,99}$/, message: '使用小写字母开头，可包含数字、点、横线和下划线' },
                ]}><Input name="geoserver-workspace" autoComplete="off" placeholder="datascalpel" /></Form.Item>
              </>}
            </div>
          </FormSection>
        </Form>
      </Drawer>
    </>
  );
};
