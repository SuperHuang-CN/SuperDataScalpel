import { Alert, Button, Checkbox, Col, Drawer, Form, Input, Row, Select, Space, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
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
  const createMutation = useCreateLlmModel();
  const updateMutation = useUpdateLlmModel();

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
      title={model ? '修改 AI 模型' : '注册 AI 模型'}
      open={open}
      size={640}
      onClose={close}
      destroyOnHidden
      footer={<Space><Button onClick={close}>取消</Button><Button type="primary" loading={pending} onClick={() => form.submit()}>保存</Button></Space>}
    >
      <Alert
        showIcon
        type="info"
        message="第一版仅支持 OpenAI Compatible Chat Completions"
        description="保存后模型处于未测试和停用状态。必须正确返回指定 Tool Call，才能启用给助手使用。"
        style={{ marginBottom: 16 }}
      />
      <Form<ModelFormValues> autoComplete="off" form={form} layout="vertical" onFinish={(values) => void save(values)}>
        <Row gutter={12}>
          <Col span={12}><Form.Item label="显示名称" name="name" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input autoFocus /></Form.Item></Col>
          <Col span={12}><Form.Item label="协议" name="protocol" rules={[{ required: true }]}><Select options={[{ value: 'OPENAI_COMPATIBLE', label: 'OpenAI Compatible' }]} /></Form.Item></Col>
          <Col span={24}><Form.Item label="Base URL" name="baseUrl" extra="系统会在该地址后调用 /chat/completions。" rules={[{ required: true, type: 'url', message: '请输入有效的 HTTP(S) 地址' }, { max: 1000 }]}><Input placeholder="https://api.example.com/v1" /></Form.Item></Col>
          <Col span={24}><Form.Item label="模型标识" name="modelName" rules={[{ required: true, whitespace: true }, { max: 200 }]}><Input placeholder="例如 gpt-4.1-mini 或内网部署模型名" /></Form.Item></Col>
          <Col span={24}><Form.Item label={model?.apiKeyConfigured ? 'API Key（留空保持不变）' : 'API Key（可选）'} name="apiKey" rules={[{ max: 4000 }]}><Input.Password name="assistant-llm-api-key" autoComplete="off" placeholder="内网无鉴权模型可留空" /></Form.Item></Col>
          {model?.apiKeyConfigured && <Col span={24}><Form.Item name="clearApiKey" valuePropName="checked"><Checkbox>清除已保存的 API Key</Checkbox></Form.Item></Col>}
          <Col span={24}>
            <Form.Item
              label="额外请求参数（JSON）"
              name="extraRequestParameters"
              extra={<span>参数会合并到 Chat Completions 请求顶层。千问关闭思考模式示例：<code>{'{"enable_thinking": false}'}</code>。请勿填写 API Key、Token 或其他凭据。</span>}
              rules={[{ max: 16_000 }, { validator: validateExtraRequestParameters }]}
            >
              <Input.TextArea
                autoComplete="off"
                rows={7}
                spellCheck={false}
                placeholder={'{\n  "enable_thinking": false\n}'}
                style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }}
              />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Drawer>
  </>;
};
