import { ApiOutlined, KeyOutlined, RobotOutlined, SlidersOutlined } from '@ant-design/icons';
import { Badge, Button, Checkbox, Col, Drawer, Form, Input, Row, Select, Space, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCreateLlmModel, useUpdateLlmModel } from '../hooks/useAssistant';
import type { LlmModelConfiguration, SaveLlmModelRequest } from '../model/assistant';

interface LlmModelDrawerProps {
  open: boolean;
  model: LlmModelConfiguration | null;
  onClose: () => void;
}

interface ModelFormValues {
  name: string;
  protocol: 'OPENAI_COMPATIBLE';
  baseUrl: string;
  modelName: string;
  apiKey?: string;
  clearApiKey?: boolean;
  extraRequestParameters: string;
}

const reservedExtraParameterNames = new Set([
  'model', 'messages', 'tools', 'tool_choice', 'stream', 'temperature', 'max_tokens', 'n',
  'authorization', 'api_key', 'apikey', 'access_token',
]);

const formatExtraRequestParameters = (value: string | null | undefined) => {
  if (!value?.trim()) return '{}';
  try {
    return JSON.stringify(JSON.parse(value) as unknown, null, 2);
  } catch {
    return value;
  }
};

const validateExtraRequestParameters = async (_: unknown, value?: string) => {
  if (!value?.trim()) return;
  let parsed: unknown;
  try {
    parsed = JSON.parse(value) as unknown;
  } catch {
    throw new Error('请输入合法的 JSON 对象');
  }
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('额外请求参数必须是 JSON 对象');
  }
  const reserved = Object.keys(parsed).find((key) => (
    reservedExtraParameterNames.has(key.trim().toLowerCase().replaceAll('-', '_'))
  ));
  if (reserved) throw new Error(`不能覆盖系统保留字段：${reserved}`);
};

export const LlmModelDrawer = ({ open, model, onClose }: LlmModelDrawerProps) => {
  const [form] = Form.useForm<ModelFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [apiKeyEditorTarget, setApiKeyEditorTarget] = useState<string | null>(null);
  const createMutation = useCreateLlmModel();
  const updateMutation = useUpdateLlmModel();
  const drawerTarget = model?.id ?? 'new';
  const editingApiKey = apiKeyEditorTarget === drawerTarget;

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(model ? {
      name: model.name,
      protocol: model.protocol,
      baseUrl: model.baseUrl,
      modelName: model.modelName,
      apiKey: undefined,
      clearApiKey: false,
      extraRequestParameters: formatExtraRequestParameters(model.extraRequestParameters),
    } : { protocol: 'OPENAI_COMPATIBLE', extraRequestParameters: '{}' });
  }, [form, model, open]);

  const close = () => {
    form.resetFields();
    setApiKeyEditorTarget(null);
    onClose();
  };

  const save = async (values: ModelFormValues) => {
    const request: SaveLlmModelRequest = {
      name: values.name.trim(),
      protocol: values.protocol,
      baseUrl: values.baseUrl.trim().replace(/\/+$/, ''),
      modelName: values.modelName.trim(),
      apiKey: values.apiKey?.trim() || undefined,
      clearApiKey: model ? Boolean(values.clearApiKey) : undefined,
      extraRequestParameters: values.extraRequestParameters?.trim() || '{}',
    };
    try {
      if (model) await updateMutation.mutateAsync({ id: model.id, request });
      else await createMutation.mutateAsync(request);
      messageApi.success(model ? 'AI 模型配置已保存，请重新测试连接' : 'AI 模型已创建，请先测试工具调用兼容性');
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存 AI 模型失败');
    }
  };

  const pending = createMutation.isPending || updateMutation.isPending;

  return <>
    {messageContext}
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      className="data-model-drawer llm-model-drawer"
      title={(
        <div className="data-model-drawer-title">
          <span className="data-model-drawer-title-icon" aria-hidden="true"><RobotOutlined /></span>
          <span className="data-model-drawer-title-copy">
            <span>{model ? '修改 AI 模型' : '注册 AI 模型'}</span>
            <Typography.Text type="secondary">配置模型身份、兼容协议与访问凭据</Typography.Text>
          </span>
        </div>
      )}
      extra={<Tag className="data-model-drawer-header-tag">OpenAI Compatible</Tag>}
      open={open}
      size={760}
      onClose={close}
      forceRender
      destroyOnHidden
      footer={(
        <div className="data-model-drawer-footer">
          <Badge status="processing" text={model ? '保存后需重新测试连接' : '创建后需测试工具调用兼容性'} />
          <Space>
            <Button onClick={close}>取消</Button>
            <Button type="primary" loading={pending} onClick={() => form.submit()}>{model ? '保存修改' : '注册模型'}</Button>
          </Space>
        </div>
      )}
    >
      <Form<ModelFormValues>
        name="llm-model-editor-form"
        className="data-model-form llm-model-form"
        autoComplete="off"
        form={form}
        layout="vertical"
        onFinish={(values) => void save(values)}
      >
        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><ApiOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title-row">
                <span className="data-model-form-section-title">模型身份与协议</span>
                <ContextHelp
                  ariaLabel="查看兼容协议说明"
                  content="当前版本通过 OpenAI Compatible Chat Completions 调用模型；保存后需通过 Tool Call 兼容性测试才能启用。"
                  presentation="popover"
                />
              </span>
              <Typography.Text type="secondary">声明管理端名称、远端模型标识与调用协议</Typography.Text>
            </span>
          </header>
          <div className="data-model-form-section-body">
            <Row gutter={14}>
              <Col xs={24} sm={12}><Form.Item label="显示名称" name="name" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input name="assistant-model-display-name" autoComplete="off" autoFocus placeholder="输入管理端显示名称" /></Form.Item></Col>
              <Col xs={24} sm={12}><Form.Item label="协议" name="protocol" rules={[{ required: true }]}><Select options={[{ value: 'OPENAI_COMPATIBLE', label: 'OpenAI Compatible' }]} /></Form.Item></Col>
              <Col span={24}><Form.Item label="模型标识" name="modelName" rules={[{ required: true, whitespace: true }, { max: 200 }]}><Input name="assistant-model-remote-name" autoComplete="off" placeholder="例如 gpt-4.1-mini 或内网部署模型名" /></Form.Item></Col>
            </Row>
          </div>
        </section>

        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><KeyOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title">连接与鉴权</span>
              <Typography.Text type="secondary">配置服务入口和可选的访问密钥</Typography.Text>
            </span>
            <Tag className="data-model-drawer-header-tag">{model?.apiKeyConfigured ? '已配置 Key' : '无 Key'}</Tag>
          </header>
          <div className="data-model-form-section-body">
            <Row gutter={14}>
              <Col span={24}><Form.Item label="Base URL" name="baseUrl" extra="系统会在该地址后调用 /chat/completions" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 1000 }]}><Input type="url" name="assistant-model-base-url" autoComplete="off" placeholder="https://api.example.com/v1" /></Form.Item></Col>
              <Col span={24}>
                {editingApiKey ? (
                  <Form.Item label={model?.apiKeyConfigured ? '新的 API Key' : 'API Key（可选）'} name="apiKey" rules={[{ max: 4000 }]}>
                    <BusinessSecretInput
                      name="assistant-llm-api-key"
                      autoComplete="off"
                      placeholder="输入模型服务访问密钥"
                      addonAfter={<Button type="text" size="small" onClick={() => { form.setFieldValue('apiKey', undefined); setApiKeyEditorTarget(null); }}>暂不配置</Button>}
                    />
                  </Form.Item>
                ) : (
                  <div className="llm-model-credential-control">
                    <InlineFeedback tone={model?.apiKeyConfigured ? 'success' : 'info'} label={model?.apiKeyConfigured ? '已安全保存 API Key' : '当前不使用 API Key'} />
                    <Button onClick={() => setApiKeyEditorTarget(drawerTarget)}>{model?.apiKeyConfigured ? '替换密钥' : '配置密钥'}</Button>
                  </div>
                )}
              </Col>
              {model?.apiKeyConfigured && <Col span={24}><Form.Item className="llm-model-clear-key" name="clearApiKey" valuePropName="checked"><Checkbox>保存时清除当前 API Key</Checkbox></Form.Item></Col>}
            </Row>
          </div>
        </section>

        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><SlidersOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title-row">
                <span className="data-model-form-section-title">请求扩展</span>
                <ContextHelp
                  ariaLabel="查看额外请求参数说明"
                  content={<span>参数会合并到 Chat Completions 请求顶层。示例：<code>{'{"enable_thinking": false}'}</code>。不能覆盖系统字段，也不能填写 API Key、Token 等凭据。</span>}
                  presentation="popover"
                />
              </span>
              <Typography.Text type="secondary">按模型提供方要求补充非敏感 JSON 参数</Typography.Text>
            </span>
          </header>
          <div className="data-model-form-section-body">
            <Form.Item label="额外请求参数（JSON）" name="extraRequestParameters" rules={[{ max: 16_000 }, { validator: validateExtraRequestParameters }]}>
              <Input.TextArea name="assistant-model-extra-parameters" autoComplete="off" rows={7} spellCheck={false} placeholder={'{\n  "enable_thinking": false\n}'} className="llm-model-json-input" />
            </Form.Item>
          </div>
        </section>
      </Form>
    </Drawer>
  </>;
};
