import {
  ControlOutlined,
  ExperimentOutlined,
  EyeInvisibleOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import {
  Badge,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  useCreateMaskingRule,
  useUpdateMaskingRule,
} from '../hooks/useMaskingRules';
import {
  createMaskingRuleDefinition,
  maskingStrategyLabels,
  previewMaskedText,
  type CreateDataMaskingRuleRequest,
  type DataMaskingRule,
  type MaskingRuleDefinition,
  type MaskingStrategy,
} from '../model/maskingRule';

interface MaskingRuleDrawerProps {
  open: boolean;
  rule: DataMaskingRule | null;
  readOnly?: boolean;
  onClose: () => void;
}

interface MaskingRuleFormValues {
  code: string;
  name: string;
  description?: string;
  strategy: MaskingStrategy;
  keepPrefixLength?: number;
  keepSuffixLength?: number;
  maskPosition?: number;
  maskCharacter?: string;
  fixedValue?: string;
}

const strategyOptions = (
  Object.entries(maskingStrategyLabels) as [MaskingStrategy, string][]
).map(([value, label]) => ({ value, label }));

const definitionFromValues = (values: MaskingRuleFormValues): MaskingRuleDefinition => {
  const definition = createMaskingRuleDefinition(values.strategy);
  switch (values.strategy) {
    case 'PARTIAL_MASK':
      return {
        ...definition,
        keepPrefixLength: values.keepPrefixLength ?? 0,
        keepSuffixLength: values.keepSuffixLength ?? 0,
        maskCharacter: values.maskCharacter ?? '*',
      };
    case 'POSITION_MASK':
      return {
        ...definition,
        maskPosition: values.maskPosition ?? 2,
        maskCharacter: values.maskCharacter ?? '*',
      };
    case 'KEEP_LENGTH_MASK':
      return { ...definition, maskCharacter: values.maskCharacter ?? '*' };
    case 'FIXED_VALUE':
      return { ...definition, fixedValue: values.fixedValue ?? '' };
    case 'NULLIFY':
      return definition;
  }
};

const valuesFromRule = (rule: DataMaskingRule | null): MaskingRuleFormValues => {
  const definition = rule?.definition ?? createMaskingRuleDefinition();
  return {
    code: rule?.code ?? '',
    name: rule?.name ?? '',
    description: rule?.description ?? undefined,
    strategy: definition.strategy,
    keepPrefixLength: definition.keepPrefixLength ?? undefined,
    keepSuffixLength: definition.keepSuffixLength ?? undefined,
    maskPosition: definition.maskPosition ?? undefined,
    maskCharacter: definition.maskCharacter ?? undefined,
    fixedValue: definition.fixedValue ?? undefined,
  };
};

export const MaskingRuleDrawer = ({
  open,
  rule,
  readOnly = false,
  onClose,
}: MaskingRuleDrawerProps) => {
  const [form] = Form.useForm<MaskingRuleFormValues>();
  const [previewValue, setPreviewValue] = useState('');
  const [messageApi, contextHolder] = message.useMessage();
  const createMutation = useCreateMaskingRule();
  const updateMutation = useUpdateMaskingRule();
  const strategy = Form.useWatch('strategy', form) ?? 'PARTIAL_MASK';
  const watchedValues = Form.useWatch([], form);
  const preview = useMemo(() => {
    if (!watchedValues?.strategy) return '';
    return previewMaskedText(previewValue, definitionFromValues(watchedValues));
  }, [previewValue, watchedValues]);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(valuesFromRule(rule));
  }, [form, open, rule]);

  const close = () => {
    setPreviewValue('');
    onClose();
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const request: CreateDataMaskingRuleRequest = {
        code: values.code.trim(),
        name: values.name.trim(),
        description: values.description?.trim() || null,
        definition: definitionFromValues(values),
      };
      if (rule) {
        await updateMutation.mutateAsync({
          id: rule.id,
          request: {
            name: request.name,
            description: request.description,
            definition: request.definition,
          },
        });
        messageApi.success('脱敏规则已更新');
      } else {
        await createMutation.mutateAsync(request);
        messageApi.success('脱敏规则已创建');
      }
      close();
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
    }
  };

  const pending = createMutation.isPending || updateMutation.isPending;
  const strategyLabel = maskingStrategyLabels[strategy];

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer masking-rule-drawer"
        open={open}
        size={720}
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><EyeInvisibleOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{rule ? `${readOnly ? '查看' : '修改'}脱敏规则` : '新建脱敏规则'}</span>
              <Typography.Text type="secondary">定义可复用的数据掩码策略与执行参数</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{strategyLabel}</Tag>}
        destroyOnHidden
        onClose={close}
        footer={(
          <div className="data-model-drawer-footer">
            <Badge
              status={readOnly ? 'default' : 'processing'}
              text={readOnly
                ? `只读查看 · ${rule?.code ?? strategyLabel}`
                : rule
                  ? `修改已有规则 · ${rule.code}`
                  : '创建后可被任务节点引用'}
            />
            <Space>
              <Button onClick={close}>{readOnly ? '关闭' : '取消'}</Button>
              {!readOnly && (
                <Button type="primary" loading={pending} onClick={() => void submit()}>
                  {rule ? '保存修改' : '创建规则'}
                </Button>
              )}
            </Space>
          </div>
        )}
      >
        <Form<MaskingRuleFormValues>
          name="masking-rule-editor-form"
          className="data-model-form masking-rule-form"
          autoComplete="off"
          disabled={readOnly}
          form={form}
          layout="vertical"
          onValuesChange={(changed) => {
            if ('strategy' in changed && changed.strategy) {
              const definition = createMaskingRuleDefinition(changed.strategy);
              form.setFieldsValue({
                strategy: changed.strategy,
                keepPrefixLength: definition.keepPrefixLength ?? undefined,
                keepSuffixLength: definition.keepSuffixLength ?? undefined,
                maskPosition: definition.maskPosition ?? undefined,
                maskCharacter: definition.maskCharacter ?? undefined,
                fixedValue: definition.fixedValue ?? undefined,
              });
            }
          }}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><FileTextOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">规则身份</span>
                <Typography.Text type="secondary">设置稳定编码、显示名称和适用场景</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="code"
                    label="规则编码"
                    rules={[
                      { required: true, whitespace: true, message: '请输入规则编码' },
                      {
                        pattern: /^[a-z][a-z0-9_]{0,63}$/,
                        message: '以小写字母开头，只能包含小写字母、数字和下划线',
                      },
                    ]}
                  >
                    <Input name="masking-rule-code" autoComplete="off" disabled={Boolean(rule) || readOnly} placeholder="例如 mask_mobile" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="name"
                    label="规则名称"
                    rules={[
                      { required: true, whitespace: true, message: '请输入规则名称' },
                      { max: 100, message: '规则名称不能超过 100 个字符' },
                    ]}
                  >
                    <Input name="masking-rule-name" autoComplete="off" placeholder="例如 手机号部分掩码" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="description" label="说明" rules={[{ max: 1000 }]}>
                    <Input.TextArea name="masking-rule-description" autoComplete="off" rows={3} showCount maxLength={1000} placeholder="说明适用数据、场景或合规要求" />
                  </Form.Item>
                </Col>
              </Row>
            </div>
          </section>

          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><ControlOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">执行定义</span>
                <Typography.Text type="secondary">选择脱敏方式，并只配置当前策略需要的参数</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Form.Item name="strategy" label="脱敏策略" rules={[{ required: true }]}>
                <Select options={strategyOptions} />
              </Form.Item>

              {strategy === 'PARTIAL_MASK' && (
                <Row gutter={14}>
                  <Col xs={24} sm={8}>
                    <Form.Item name="keepPrefixLength" label="保留前缀字符数" rules={[{ required: true, message: '请输入保留前缀字符数' }]}>
                      <InputNumber min={0} max={1024} precision={0} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Form.Item name="keepSuffixLength" label="保留后缀字符数" rules={[{ required: true, message: '请输入保留后缀字符数' }]}>
                      <InputNumber min={0} max={1024} precision={0} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} sm={8}>
                    <MaskCharacterField />
                  </Col>
                </Row>
              )}

              {strategy === 'POSITION_MASK' && (
                <Row gutter={14}>
                  <Col xs={24} sm={12}>
                    <div className="data-model-form-help-field">
                      <div className="data-model-form-external-label">
                        <label htmlFor="masking-rule-editor-form_maskPosition"><span aria-hidden="true">*</span>掩码位置</label>
                        <ContextHelp ariaLabel="查看掩码位置说明" content="从 1 开始计数；文本长度不足时保留原值。" />
                      </div>
                      <Form.Item name="maskPosition" rules={[{ required: true, message: '请输入掩码位置' }]}>
                        <InputNumber aria-label="掩码位置" min={1} max={1024} precision={0} style={{ width: '100%' }} />
                      </Form.Item>
                    </div>
                  </Col>
                  <Col xs={24} sm={12}>
                    <MaskCharacterField />
                  </Col>
                </Row>
              )}

              {strategy === 'KEEP_LENGTH_MASK' && (
                <Row gutter={14}>
                  <Col xs={24} sm={12}><MaskCharacterField /></Col>
                </Row>
              )}

              {strategy === 'FIXED_VALUE' && (
                <Form.Item name="fixedValue" label="固定替换值" rules={[{ max: 1024 }]}>
                  <Input.TextArea name="masking-rule-fixed-value" autoComplete="off" rows={2} placeholder="输入用于替换原值的固定文本" />
                </Form.Item>
              )}

              {strategy === 'NULLIFY' && (
                <InlineFeedback tone="info" label="命中该规则时，输入值将被转换为 NULL" />
              )}
            </div>
          </section>

          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><ExperimentOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">手工预览</span>
                <Typography.Text type="secondary">本地验证当前参数效果，测试文本不会提交或保存</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body masking-rule-preview-body">
              <Input
                name="masking-rule-preview-value"
                autoComplete="off"
                value={previewValue}
                placeholder="输入测试文本"
                onChange={(event) => setPreviewValue(event.target.value)}
              />
              <div className="masking-rule-preview-result" aria-live="polite">
                <span>脱敏结果</span>
                <code>{preview === null ? 'NULL' : preview || '—'}</code>
              </div>
            </div>
          </section>
        </Form>
      </Drawer>
    </>
  );
};

const MaskCharacterField = () => (
  <Form.Item
    name="maskCharacter"
    label="掩码字符"
    rules={[
      { required: true, message: '请输入掩码字符' },
      {
        validator: async (_, value: string | undefined) => {
          if (value && Array.from(value).length !== 1) {
            throw new Error('掩码字符必须是一个 Unicode 字符');
          }
        },
      },
    ]}
  >
    <Input name="masking-rule-mask-character" autoComplete="off" maxLength={2} />
  </Form.Item>
);
