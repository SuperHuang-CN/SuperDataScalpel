import { Alert, Button, Divider, Drawer, Form, Input, InputNumber, Select, Space, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
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

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        open={open}
        width={560}
        title={rule
          ? `${readOnly ? '查看' : '修改'}脱敏规则 · ${rule.name}`
          : '新建脱敏规则'}
        destroyOnHidden
        onClose={close}
        footer={(
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Space>
              <Button onClick={close}>{readOnly ? '关闭' : '取消'}</Button>
              {!readOnly && (
                <Button type="primary" loading={pending} onClick={() => void submit()}>
                  保存
                </Button>
              )}
            </Space>
          </div>
        )}
      >
        <Form<MaskingRuleFormValues>
          autoComplete="off"
          disabled={readOnly}
          form={form}
          layout="vertical"
          initialValues={valuesFromRule(rule)}
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
          <div className="form-grid two-columns">
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
              <Input disabled={Boolean(rule) || readOnly} placeholder="例如 mask_mobile" />
            </Form.Item>
            <Form.Item
              name="name"
              label="规则名称"
              rules={[
                { required: true, whitespace: true, message: '请输入规则名称' },
                { max: 100, message: '规则名称不能超过 100 个字符' },
              ]}
            >
              <Input placeholder="例如 手机号部分掩码" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="说明" rules={[{ max: 1000 }]}>
            <Input.TextArea rows={3} placeholder="说明适用场景" />
          </Form.Item>
          <Divider titlePlacement="start">执行定义</Divider>
          <Form.Item name="strategy" label="脱敏策略" rules={[{ required: true }]}>
            <Select options={strategyOptions} />
          </Form.Item>
          {strategy === 'PARTIAL_MASK' && (
            <div className="form-grid two-columns">
              <Form.Item
                name="keepPrefixLength"
                label="保留前缀字符数"
                rules={[{ required: true, message: '请输入保留前缀字符数' }]}
              >
                <InputNumber min={0} max={1024} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item
                name="keepSuffixLength"
                label="保留后缀字符数"
                rules={[{ required: true, message: '请输入保留后缀字符数' }]}
              >
                <InputNumber min={0} max={1024} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </div>
          )}
          {strategy === 'POSITION_MASK' && (
            <Form.Item
              name="maskPosition"
              label="掩码位置"
              extra="从 1 开始计数；文本长度不足时保留原值。"
              rules={[{ required: true, message: '请输入掩码位置' }]}
            >
              <InputNumber min={1} max={1024} precision={0} style={{ width: 160 }} />
            </Form.Item>
          )}
          {(strategy === 'PARTIAL_MASK'
            || strategy === 'POSITION_MASK'
            || strategy === 'KEEP_LENGTH_MASK') && (
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
              <Input maxLength={2} style={{ width: 120 }} />
            </Form.Item>
          )}
          {strategy === 'FIXED_VALUE' && (
            <Form.Item
              name="fixedValue"
              label="固定替换值"
              rules={[{ max: 1024 }]}
            >
              <Input.TextArea rows={2} />
            </Form.Item>
          )}
          <Divider titlePlacement="start">手工预览</Divider>
          <Input
            value={previewValue}
            placeholder="输入测试文本，不会提交或保存"
            onChange={(event) => setPreviewValue(event.target.value)}
          />
          <Alert
            style={{ marginTop: 8 }}
            type="info"
            showIcon
            title="脱敏结果"
            description={preview === null
              ? <Typography.Text code>NULL</Typography.Text>
              : <Typography.Text code>{preview}</Typography.Text>}
          />
        </Form>
      </Drawer>
    </>
  );
};
