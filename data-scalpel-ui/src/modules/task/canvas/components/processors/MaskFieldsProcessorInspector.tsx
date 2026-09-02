import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SyncOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Form, Input, InputNumber, Popover, Radio, Select, Space, Tag, Typography, message } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import { ApiError } from '../../../../../shared/api/http';
import { fetchMaskingRule } from '../../../api/maskingRuleApi';
import { useMaskingRules } from '../../../hooks/useMaskingRules';
import {
  createMaskingRuleDefinition,
  maskingRuleDefinitionsEqual,
  maskingStrategyLabels,
  type DataMaskingRule,
  type MaskingRuleDefinition,
  type MaskingStrategy,
} from '../../../model/maskingRule';
import {
  CANVAS_MASKING_MAX_FIELD_RULES,
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type MaskFieldRule,
  type MaskFieldsConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface MaskFieldsProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'MASK_FIELDS' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface MaskFieldsFormValues {
  sourceTableName: string;
  outputTableName: string;
}

interface DefinitionEditorProps {
  definition: MaskingRuleDefinition;
  disabled?: boolean;
  onChange: (definition: MaskingRuleDefinition) => void;
}

const strategyOptions = (
  Object.entries(maskingStrategyLabels) as [MaskingStrategy, string][]
).map(([value, label]) => ({ value, label }));

const strategyHelp: Record<MaskingStrategy, {
  usage: string;
  example: string;
  boundary: string;
}> = {
  PARTIAL_MASK: {
    usage: '保留开头和结尾指定数量的字符，中间字符逐个替换为掩码字符；原值太短、无法同时保留前后部分时，会将整个值等长掩码。',
    example: '13812345678 → 138****5678',
    boundary: '仅 STRING；需设置前缀、后缀和一个掩码字符。',
  },
  POSITION_MASK: {
    usage: '将指定位置的一个字符替换为掩码字符；位置从 1 开始计数，原值长度不足时保留原值。',
    example: '张三 → 张*；张三丰 → 张*丰',
    boundary: '仅 STRING；需设置位置和一个掩码字符。',
  },
  KEEP_LENGTH_MASK: {
    usage: '把原值的每个字符都替换为掩码字符，保留字符数量，不保留任何原始字符。',
    example: 'Alice → *****',
    boundary: '仅 STRING；需设置一个掩码字符。',
  },
  FIXED_VALUE: {
    usage: '把所有非 NULL 值统一替换为指定文本，适合用“保密”“已隐藏”等固定标记覆盖原内容。',
    example: 'Alice → 已隐藏',
    boundary: '仅 STRING；允许空字符串，最长 1024 个字符。',
  },
  NULLIFY: {
    usage: '将字段值替换为同类型的 SQL NULL，不保留原值。',
    example: '任意非 NULL 值 → NULL',
    boundary: '支持任意平台类型，但字段必须允许为空。',
  },
};

const MaskingStrategyHelp = ({ activeStrategy }: { activeStrategy: MaskingStrategy }) => (
  <div className="canvas-masking-strategy-help">
    <div className="canvas-masking-strategy-help-heading">脱敏策略说明</div>
    {(Object.entries(strategyHelp) as [MaskingStrategy, (typeof strategyHelp)[MaskingStrategy]][])
      .map(([strategy, help]) => (
        <div
          className={`canvas-masking-strategy-help-item${strategy === activeStrategy ? ' is-active' : ''}`}
          key={strategy}
        >
          <div className="canvas-masking-strategy-help-title">
            <strong>{maskingStrategyLabels[strategy]}</strong>
            {strategy === activeStrategy && <Tag color="blue">当前</Tag>}
          </div>
          <span>{help.usage}</span>
          <code>{help.example}</code>
          <small>{help.boundary}</small>
        </div>
      ))}
    <div className="canvas-masking-strategy-help-footer">
      所有策略都会保持输入 NULL 为 NULL，不会把 NULL 转换成文本。
    </div>
  </div>
);

const MaskingDefinitionEditor = ({
  definition,
  disabled = false,
  onChange,
}: DefinitionEditorProps) => (
  <Space orientation="vertical" size={8} style={{ width: '100%' }}>
    <div className="canvas-masking-strategy-control">
      <Select
        disabled={disabled}
        value={definition.strategy}
        options={strategyOptions}
        onChange={(strategy: MaskingStrategy) => onChange(createMaskingRuleDefinition(strategy))}
      />
      <Popover
        trigger={['hover', 'click']}
        placement="bottomRight"
        arrow={false}
        rootClassName="canvas-masking-strategy-help-overlay"
        content={<MaskingStrategyHelp activeStrategy={definition.strategy} />}
      >
        <Button
          type="text"
          className="canvas-masking-strategy-help-button"
          icon={<QuestionCircleOutlined />}
          aria-label="查看脱敏策略说明"
        />
      </Popover>
    </div>
    {definition.strategy === 'PARTIAL_MASK' && (
      <Space.Compact block>
        <InputNumber
          disabled={disabled}
          min={0}
          max={1024}
          precision={0}
          addonBefore="前缀"
          value={definition.keepPrefixLength}
          onChange={(value) => onChange({
            ...definition,
            keepPrefixLength: value,
          })}
        />
        <InputNumber
          disabled={disabled}
          min={0}
          max={1024}
          precision={0}
          addonBefore="后缀"
          value={definition.keepSuffixLength}
          onChange={(value) => onChange({
            ...definition,
            keepSuffixLength: value,
          })}
        />
      </Space.Compact>
    )}
    {definition.strategy === 'POSITION_MASK' && (
      <InputNumber
        disabled={disabled}
        min={1}
        max={1024}
        precision={0}
        addonBefore="位置"
        value={definition.maskPosition}
        onChange={(value) => onChange({
          ...definition,
          maskPosition: value,
        })}
      />
    )}
    {(definition.strategy === 'PARTIAL_MASK'
      || definition.strategy === 'POSITION_MASK'
      || definition.strategy === 'KEEP_LENGTH_MASK') && (
      <Input
        disabled={disabled}
        addonBefore="掩码字符"
        value={definition.maskCharacter ?? ''}
        maxLength={2}
        onChange={(event) => onChange({
          ...definition,
          maskCharacter: event.target.value,
        })}
      />
    )}
    {definition.strategy === 'FIXED_VALUE' && (
      <Input.TextArea
        disabled={disabled}
        rows={2}
        maxLength={1024}
        value={definition.fixedValue ?? ''}
        placeholder="固定替换值，允许空字符串"
        onChange={(event) => onChange({
          ...definition,
          fixedValue: event.target.value,
        })}
      />
    )}
    {definition.strategy === 'NULLIFY' && (
      <Typography.Text type="secondary">字段值将被替换为同类型 SQL NULL。</Typography.Text>
    )}
  </Space>
);

const incompatibilityReason = (
  definition: MaskingRuleDefinition,
  column: CanvasColumnSchema | undefined,
) => {
  if (!column) return '请先选择有效字段';
  if (definition.strategy === 'NULLIFY') {
    return column.nullable ? null : '要求可空字段';
  }
  return column.fieldType === 'STRING' ? null : '仅支持 STRING 字段';
};

const compatible = (
  definition: MaskingRuleDefinition,
  column: CanvasColumnSchema | undefined,
) => incompatibilityReason(definition, column) === null;

const invalidDefinitionMessage = (
  definition: MaskingRuleDefinition,
  column: CanvasColumnSchema | undefined,
) => {
  if (!column) return '请选择有效字段';
  if (!compatible(definition, column)) {
    return definition.strategy === 'NULLIFY'
      ? 'NULLIFY 只能用于可空字段'
      : '当前策略仅支持 STRING 字段';
  }
  if ((definition.strategy === 'PARTIAL_MASK'
      || definition.strategy === 'POSITION_MASK'
      || definition.strategy === 'KEEP_LENGTH_MASK')
    && Array.from(definition.maskCharacter ?? '').length !== 1) {
    return '掩码字符必须是一个 Unicode 字符';
  }
  if (definition.strategy === 'PARTIAL_MASK') {
    if (!Number.isInteger(definition.keepPrefixLength)
      || (definition.keepPrefixLength ?? -1) < 0
      || (definition.keepPrefixLength ?? 1025) > 1024
      || !Number.isInteger(definition.keepSuffixLength)
      || (definition.keepSuffixLength ?? -1) < 0
      || (definition.keepSuffixLength ?? 1025) > 1024) {
      return '前后保留字符数必须是 0..1024 的整数';
    }
  }
  if (definition.strategy === 'POSITION_MASK'
    && (!Number.isInteger(definition.maskPosition)
      || (definition.maskPosition ?? 0) < 1
      || (definition.maskPosition ?? 1025) > 1024)) {
    return '掩码位置必须是 1..1024 的整数';
  }
  if (definition.strategy === 'FIXED_VALUE'
    && (definition.fixedValue === null || definition.fixedValue.length > 1024)) {
    return '固定替换值不能超过 1024 个字符';
  }
  return null;
};

export const MaskFieldsProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: MaskFieldsProcessorInspectorProps) => {
  const [form] = Form.useForm<MaskFieldsFormValues>();
  const [fieldRules, setFieldRules] = useState<MaskFieldRule[]>(
    () => structuredClone(node.configuration.fieldRules),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const queryClient = useQueryClient();
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const columns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const columnNames = useMemo(() => new Set(columns.map((column) => column.name)), [columns]);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);
  const maskingRulesQuery = useMaskingRules({
    page: 0,
    size: 500,
    sort: 'name,code',
  });
  const globalRuleIds = useMemo(() => [...new Set(fieldRules
    .filter((rule) => rule.ruleSource === 'GLOBAL' && rule.sourceRuleRef?.ruleId)
    .map((rule) => rule.sourceRuleRef?.ruleId as string))], [fieldRules]);
  const sourceRuleQueries = useQueries({
    queries: globalRuleIds.map((ruleId) => ({
      queryKey: ['masking-rules', ruleId],
      queryFn: () => fetchMaskingRule(ruleId),
      retry: false,
    })),
  });
  const sourceRuleChecks = useMemo(() => new Map(globalRuleIds.map(
    (ruleId, index) => [ruleId, sourceRuleQueries[index]],
  )), [globalRuleIds, sourceRuleQueries]);

  const updateRules = (next: MaskFieldRule[]) => {
    setFieldRules(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (fieldRules.length === 0) {
          setDraftError('至少配置一条字段脱敏规则');

        }
        if (fieldRules.length > CANVAS_MASKING_MAX_FIELD_RULES) {
          setDraftError(`字段规则不能超过 ${CANVAS_MASKING_MAX_FIELD_RULES} 项`);

        }
        const selectedFields = new Set<string>();
        for (const rule of fieldRules) {
          if (!rule.fieldName || !columnNames.has(rule.fieldName)) {
            setDraftError(`脱敏字段 ${rule.fieldName || '未选择'} 不存在`);

          }
          if (!selectedFields.add(rule.fieldName)) {
            setDraftError(`字段 ${rule.fieldName} 只能配置一条脱敏规则`);

          }
          if (executionMode === 'STREAMING'
            && sourceTable?.eventTimeColumn === rule.fieldName) {
            setDraftError(`实时任务不能脱敏事件时间字段 ${rule.fieldName}`);

          }
          if (rule.ruleSource === 'GLOBAL' && !rule.sourceRuleRef?.ruleId) {
            setDraftError(`字段 ${rule.fieldName} 请选择全局规则`);

          }
          const invalidMessage = invalidDefinitionMessage(
            rule.definition,
            columns.find((column) => column.name === rule.fieldName),
          );
          if (invalidMessage) {
            setDraftError(`字段 ${rule.fieldName}：${invalidMessage}`);

          }
        }
        const configuration: MaskFieldsConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          fieldRules: structuredClone(fieldRules),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.MaskFields,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    columnNames,
    columns,
    executionMode,
    fieldRules,
    form,
    node.id,
    onApply,
    onDirtyChange,
    sourceTable?.eventTimeColumn,
  ]);

  const selectGlobalRule = async (
    ruleIndex: number,
    ruleId: string,
  ) => {
    try {
      const selected = await queryClient.fetchQuery({
        queryKey: ['masking-rules', ruleId],
        queryFn: () => fetchMaskingRule(ruleId),
      });
      updateRules(fieldRules.map((rule, index) => index === ruleIndex ? {
        ...rule,
        ruleSource: 'GLOBAL',
        sourceRuleRef: {
          ruleId: selected.id,
          ruleCode: selected.code,
          ruleName: selected.name,
        },
        definition: structuredClone(selected.definition),
      } : rule));
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '读取脱敏规则失败');
    }
  };

  const synchronizeRule = async (ruleIndex: number, ruleId: string) => {
    try {
      const selected = await queryClient.fetchQuery({
        queryKey: ['masking-rules', ruleId],
        queryFn: () => fetchMaskingRule(ruleId),
      });
      updateRules(fieldRules.map((rule, index) => index === ruleIndex ? {
        ...rule,
        sourceRuleRef: {
          ruleId: selected.id,
          ruleCode: selected.code,
          ruleName: selected.name,
        },
        definition: structuredClone(selected.definition),
      } : rule));
      messageApi.success('已同步当前全局规则');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '同步脱敏规则失败');
    }
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      {contextHolder}
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<MaskFieldsFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
        }}
        onValuesChange={() => onDirtyChange(true)}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，现有规则仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder={validation ? '选择一张上游表' : '等待 Task Engine 返回上游表'}
            options={tableOptions}
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { max: 255, message: '输出表名不能超过 255 个字符' },
          ]}
        >
          <Input placeholder="例如 customers_masked" />
        </Form.Item>
      </Form>

      {maskingRulesQuery.error && (
        <Alert
          type="warning"
          showIcon
          title="全局脱敏规则加载失败"
          description="现有节点配置仍可编辑和保存；暂时无法选择新的全局规则。"
          action={<Button onClick={() => void maskingRulesQuery.refetch()}>重试</Button>}
        />
      )}

      <section className="canvas-processor-editor-section">
        <div className="canvas-processor-editor-heading">
          <span>
            <Typography.Text strong>字段规则</Typography.Text>
            <Typography.Text type="secondary">
              {` · ${fieldRules.length}/${CANVAS_MASKING_MAX_FIELD_RULES}`}
            </Typography.Text>
          </span>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={fieldRules.length >= CANVAS_MASKING_MAX_FIELD_RULES}
            onClick={() => {
              const column = columns.find((candidate) => !fieldRules.some(
                (rule) => rule.fieldName === candidate.name,
              ));
              updateRules([...fieldRules, {
                fieldName: column?.name ?? '',
                ruleSource: 'INLINE',
                sourceRuleRef: null,
                definition: createMaskingRuleDefinition(
                  column?.fieldType === 'STRING' ? 'PARTIAL_MASK' : 'NULLIFY',
                ),
              }]);
            }}
          >
            添加字段
          </Button>
        </div>
        {fieldRules.length === 0 && (
          <Typography.Text type="secondary">
            添加字段后选择全局规则，或直接配置节点内自定义规则。
          </Typography.Text>
        )}
        <div className="canvas-processor-rule-list">
          {fieldRules.map((rule, ruleIndex) => {
            const column = columns.find((candidate) => candidate.name === rule.fieldName);
            const missing = Boolean(rule.fieldName && !columnNames.has(rule.fieldName));
            const eventTimeViolation = executionMode === 'STREAMING'
              && sourceTable?.eventTimeColumn === rule.fieldName;
            const definitionError = invalidDefinitionMessage(rule.definition, column);
            const sourceQuery = rule.sourceRuleRef
              ? sourceRuleChecks.get(rule.sourceRuleRef.ruleId)
              : undefined;
            const sourceDeleted = sourceQuery?.error instanceof ApiError
              && sourceQuery.error.status === 404;
            const sourceUnavailable = Boolean(sourceQuery?.error && !sourceDeleted);
            const sourceChanged = Boolean(
              sourceQuery?.data
              && !maskingRuleDefinitionsEqual(
                sourceQuery.data.definition,
                rule.definition,
              ),
            );
            const updateRule = (next: MaskFieldRule) => updateRules(
              fieldRules.map((candidate, index) => index === ruleIndex ? next : candidate),
            );
            const fieldOptions = [
              ...(missing ? [{
                value: rule.fieldName,
                label: `${rule.fieldName}（已失效）`,
                disabled: true,
              }] : []),
              ...columns.map((candidate) => ({
                value: candidate.name,
                label: `${candidate.name} · ${candidate.fieldType}${candidate.nullable ? ' · 可空' : ''}`,
                disabled: fieldRules.some(
                  (other, index) => index !== ruleIndex
                    && other.fieldName === candidate.name,
                ),
              })),
            ];
            const globalOptions = [
              ...(rule.sourceRuleRef
                && !maskingRulesQuery.data?.content.some(
                  (candidate) => candidate.id === rule.sourceRuleRef?.ruleId,
                )
                ? [{
                  value: rule.sourceRuleRef.ruleId,
                  label: `${rule.sourceRuleRef.ruleName}（当前列表不可用）`,
                  disabled: true,
                }]
                : []),
              ...(maskingRulesQuery.data?.content ?? []).map((candidate: DataMaskingRule) => {
                const incompatibility = incompatibilityReason(candidate.definition, column);
                return {
                  value: candidate.id,
                  label: [
                    candidate.name,
                    candidate.code,
                    maskingStrategyLabels[candidate.strategy],
                    incompatibility ? `不可用：${incompatibility}` : null,
                  ].filter(Boolean).join(' · '),
                  disabled: incompatibility !== null,
                };
              }),
            ];
            return (
              <Card
                key={ruleIndex}
                size="small"
                className={`canvas-processor-rule-card${
                  missing || eventTimeViolation || definitionError ? ' is-invalid' : ''
                }`}
                title={(
                  <Space>
                    <Tag color="purple">{ruleIndex + 1}</Tag>
                    <span>{rule.fieldName || '未选择字段'}</span>
                    <Tag>{rule.ruleSource === 'GLOBAL' ? '全局' : '自定义'}</Tag>
                  </Space>
                )}
                extra={(
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    aria-label={`删除字段脱敏规则 ${ruleIndex + 1}`}
                    onClick={() => updateRules(
                      fieldRules.filter((_, index) => index !== ruleIndex),
                    )}
                  />
                )}
              >
                <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    value={rule.fieldName || undefined}
                    placeholder="选择脱敏字段"
                    options={fieldOptions}
                    onChange={(fieldName: string) => updateRule({ ...rule, fieldName })}
                  />
                  {eventTimeViolation && (
                    <Typography.Text type="danger">
                      实时任务不能脱敏事件时间字段。
                    </Typography.Text>
                  )}
                  <Radio.Group
                    value={rule.ruleSource}
                    optionType="button"
                    buttonStyle="solid"
                    options={[
                      { value: 'GLOBAL', label: '全局规则' },
                      { value: 'INLINE', label: '自定义规则' },
                    ]}
                    onChange={(event) => {
                      if (event.target.value === 'INLINE') {
                        updateRule({
                          ...rule,
                          ruleSource: 'INLINE',
                          sourceRuleRef: null,
                        });
                      } else {
                        updateRule({
                          ...rule,
                          ruleSource: 'GLOBAL',
                          sourceRuleRef: null,
                        });
                      }
                    }}
                  />
                  {rule.ruleSource === 'GLOBAL' && (
                    <>
                      <Select
                        showSearch
                        optionFilterProp="label"
                        loading={maskingRulesQuery.isFetching}
                        value={rule.sourceRuleRef?.ruleId}
                        placeholder="选择全局脱敏规则"
                        options={globalOptions}
                        onChange={(ruleId: string) => void selectGlobalRule(ruleIndex, ruleId)}
                      />
                      {sourceChanged && (
                        <Alert
                          type="warning"
                          showIcon
                          title="全局规则已修改"
                          description="当前任务仍使用已保存的节点配置。"
                          action={(
                            <Button
                              size="small"
                              icon={<SyncOutlined />}
                              onClick={() => void synchronizeRule(
                                ruleIndex,
                                rule.sourceRuleRef?.ruleId ?? '',
                              )}
                            >
                              同步当前规则
                            </Button>
                          )}
                        />
                      )}
                      {sourceDeleted && (
                        <Alert
                          type="warning"
                          showIcon
                          title="来源规则已删除"
                          description="当前节点配置仍可运行，可转为自定义规则继续维护。"
                        />
                      )}
                      {sourceUnavailable && (
                        <Alert
                          type="warning"
                          showIcon
                          title="暂时无法校验来源规则"
                          description="没有将读取失败解释为规则已删除。"
                        />
                      )}
                    </>
                  )}
                  <MaskingDefinitionEditor
                    disabled={rule.ruleSource === 'GLOBAL'}
                    definition={rule.definition}
                    onChange={(definition) => updateRule({ ...rule, definition })}
                  />
                  {definitionError && (
                    <Typography.Text type="danger">{definitionError}</Typography.Text>
                  )}
                  {rule.ruleSource === 'GLOBAL' && (
                    <Button
                      size="small"
                      icon={<SwapOutlined />}
                      onClick={() => updateRule({
                        ...rule,
                        ruleSource: 'INLINE',
                        sourceRuleRef: null,
                      })}
                    >
                      转为自定义
                    </Button>
                  )}
                </Space>
              </Card>
            );
          })}
        </div>
      </section>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      <Alert
        type="info"
        showIcon
        title="运行只使用节点配置"
        description="全局规则的修改或删除不会自动改变任务；只有点击同步才会覆盖当前节点配置。"
      />
    </Space>
  );
};
