import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  InfoCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Tag, Tooltip, Typography } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE,
  CANVAS_VALUE_MAPPING_MAX_RULES,
  CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES,
  CanvasNodeType,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type ValueMappingConfiguration,
  type ValueMappingRule,
  type ValueMappingUnmatchedStrategy,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';
import { createLiteral } from './canvasLiteral';
import { TypedLiteralInput } from './TypedLiteralInput';

interface ValueMappingProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'VALUE_MAPPING' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface ValueMappingFormValues {
  sourceTableName: string;
  outputTableName: string;
}

export const ValueMappingProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: ValueMappingProcessorInspectorProps) => {
  const [form] = Form.useForm<ValueMappingFormValues>();
  const [rules, setRules] = useState<ValueMappingRule[]>(
    () => structuredClone(node.configuration.rules),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const columns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const columnNames = useMemo(
    () => new Set(columns.map((column) => column.name)),
    [columns],
  );
  const totalEntries = rules.reduce((count, rule) => count + rule.entries.length, 0);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);

  const updateRules = (next: ValueMappingRule[]) => {
    setRules(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (rules.length === 0) {
          setDraftError('至少配置一个字段的值映射');

        }
        if (rules.length > CANVAS_VALUE_MAPPING_MAX_RULES) {
          setDraftError(`字段规则不能超过 ${CANVAS_VALUE_MAPPING_MAX_RULES} 项`);

        }
        if (totalEntries > CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES) {
          setDraftError(`单节点映射项总数不能超过 ${CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES}`);

        }
        const mappedColumns = new Set<string>();
        for (const rule of rules) {
          if (!rule.columnName) {
            setDraftError('每条规则都必须选择字段');

          }
          if (!mappedColumns.add(rule.columnName)) {
            setDraftError(`字段 ${rule.columnName} 只能配置一条映射规则`);

          }
          if (rule.entries.length === 0) {
            setDraftError(`字段 ${rule.columnName} 至少需要一个映射项`);

          }
          if (rule.entries.length > CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE) {
            setDraftError(
              `字段 ${rule.columnName} 的映射项不能超过 ${
                CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE
              }`,
            );

          }
          const sourceValues = new Set<string>();
          for (const entry of rule.entries) {
            if (entry.sourceValue.value === null) {
              setDraftError(`字段 ${rule.columnName} 存在未填写的源值`);

            }
            const key = `${entry.sourceValue.dataType}\u0000${entry.sourceValue.value}`;
            if (!sourceValues.add(key)) {
              setDraftError(`字段 ${rule.columnName} 存在重复源值`);

            }
          }
          if (rule.unmatchedStrategy === 'SET_LITERAL' && !rule.unmatchedValue) {
            setDraftError(`字段 ${rule.columnName} 的未匹配默认值不能为空`);

          }
          if (rule.unmatchedStrategy !== 'SET_LITERAL' && rule.unmatchedValue) {
            setDraftError(`字段 ${rule.columnName} 当前策略不能携带默认值`);

          }
        }
        const configuration: ValueMappingConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          rules: structuredClone(rules),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.ValueMapping,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [form, node.id, onApply, onDirtyChange, rules, totalEntries]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<ValueMappingFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，映射内容仍被保留。' : undefined}
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
          <Input placeholder="例如 standardized_customers" />
        </Form.Item>
      </Form>

      <section className="canvas-processor-editor-section">
        <div className="canvas-processor-editor-heading">
          <span>
            <Typography.Text strong>字段映射</Typography.Text>
            <Typography.Text type="secondary">
              {` · ${rules.length}/${CANVAS_VALUE_MAPPING_MAX_RULES} 字段 · ${totalEntries}/${CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES} 项`}
            </Typography.Text>
            <Tooltip title="NULL 始终保持 NULL，不参与普通值匹配；大型或频繁变化的字典请使用 Input + Join。映射值不会进入节点摘要和日志。">
              <InfoCircleOutlined className="canvas-value-mapping-help" aria-label="查看值映射说明" />
            </Tooltip>
          </span>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={rules.length >= CANVAS_VALUE_MAPPING_MAX_RULES}
            onClick={() => {
              const column = columns.find(
                (candidate) => candidate.fieldType !== 'GEOMETRY'
                  && !rules.some((rule) => rule.columnName === candidate.name),
              );
              updateRules([...rules, {
                columnName: column?.name ?? '',
                entries: [],
                unmatchedStrategy: 'KEEP',
                unmatchedValue: null,
              }]);
            }}
          >
            添加字段
          </Button>
        </div>
        {rules.length === 0 && (
          <Typography.Text type="secondary">
            每个字段拥有独立映射表和未匹配策略。
          </Typography.Text>
        )}
        <div className="canvas-processor-rule-list">
          {rules.map((rule, ruleIndex) => {
            const column = columns.find((candidate) => candidate.name === rule.columnName);
            const missing = Boolean(rule.columnName && !columnNames.has(rule.columnName));
            const eventTimeViolation = executionMode === 'STREAMING'
              && sourceTable?.eventTimeColumn === rule.columnName;
            const dataType = column?.fieldType
              ?? rule.entries[0]?.sourceValue.dataType
              ?? rule.unmatchedValue?.dataType
              ?? 'STRING';
            const updateRule = (next: ValueMappingRule) => updateRules(
              rules.map((candidate, index) => index === ruleIndex ? next : candidate),
            );
            const fieldOptions = [
              ...(missing
                ? [{
                  value: rule.columnName,
                  label: `${rule.columnName}（已失效）`,
                  disabled: true,
                }]
                : []),
              ...columns.map((candidate) => ({
                value: candidate.name,
                label: `${candidate.name} · ${candidate.fieldType}`,
                disabled: candidate.fieldType === 'GEOMETRY'
                  || rules.some(
                    (other, index) => index !== ruleIndex
                      && other.columnName === candidate.name,
                  ),
              })),
            ];
            return (
              <Card
                size="small"
                className={`canvas-processor-rule-card${
                  missing || eventTimeViolation ? ' is-invalid' : ''
                }`}
                key={ruleIndex}
                title={(
                  <Space>
                    <Tag color="purple">{ruleIndex + 1}</Tag>
                    <Typography.Text code>{rule.columnName || '未选择字段'}</Typography.Text>
                    <Tag>{dataType}</Tag>
                    <Typography.Text type="secondary">{rule.entries.length} 项</Typography.Text>
                    {missing && <Tag color="error">已失效</Tag>}
                  </Space>
                )}
                extra={(
                  <Button
                    type="text"
                    danger
                    size="small"
                    aria-label={`删除字段映射 ${ruleIndex + 1}`}
                    icon={<DeleteOutlined />}
                    onClick={() => updateRules(
                      rules.filter((_, index) => index !== ruleIndex),
                    )}
                  />
                )}
              >
                <div className="canvas-value-mapping-rule-body">
                  <Select
                    showSearch
                    optionFilterProp="label"
                    value={rule.columnName || undefined}
                    placeholder="选择映射字段"
                    options={fieldOptions}
                    onChange={(columnName: string) => {
                      const nextType = columns.find(
                        (candidate) => candidate.name === columnName,
                      )?.fieldType ?? dataType;
                      updateRule({
                        ...rule,
                        columnName,
                        entries: rule.entries.map((entry) => ({
                          sourceValue: createLiteral(
                            nextType,
                            entry.sourceValue.value ?? '',
                          ),
                          targetValue: entry.targetValue === null
                            ? null
                            : createLiteral(
                              nextType,
                              entry.targetValue.value ?? '',
                            ),
                        })),
                        unmatchedValue: rule.unmatchedValue === null
                          ? null
                          : createLiteral(
                            nextType,
                            rule.unmatchedValue.value ?? '',
                          ),
                      });
                    }}
                  />
                  {eventTimeViolation && (
                    <Typography.Text type="danger">
                      实时任务不能映射事件时间字段。
                    </Typography.Text>
                  )}
                  <div className="canvas-value-mapping-table">
                    <div className="canvas-value-mapping-table-header">
                      <span>#</span>
                      <span>原值</span>
                      <span>目标方式</span>
                      <span>目标值</span>
                      <span>操作</span>
                    </div>
                    <div className="canvas-value-mapping-table-body">
                      {rule.entries.map((entry, entryIndex) => (
                        <div className="canvas-value-mapping-table-row" key={entryIndex}>
                          <span className="canvas-value-mapping-index">{entryIndex + 1}</span>
                          <TypedLiteralInput
                            dataType={dataType}
                            value={entry.sourceValue}
                            showTypeLabel={false}
                            placeholder="输入原值"
                            onChange={(sourceValue) => updateRule({
                              ...rule,
                              entries: rule.entries.map((candidate, index) => (
                                index === entryIndex
                                  ? { ...candidate, sourceValue }
                                  : candidate
                              )),
                            })}
                          />
                          <Select
                            size="small"
                            value={entry.targetValue === null ? 'NULL' : 'VALUE'}
                            options={[
                              { value: 'VALUE', label: '固定值' },
                              { value: 'NULL', label: 'SQL NULL' },
                            ]}
                            onChange={(targetMode: 'VALUE' | 'NULL') => updateRule({
                              ...rule,
                              entries: rule.entries.map((candidate, index) => (
                                index === entryIndex
                                  ? {
                                    ...candidate,
                                    targetValue: targetMode === 'NULL'
                                      ? null
                                      : candidate.targetValue ?? createLiteral(dataType),
                                  }
                                  : candidate
                              )),
                            })}
                          />
                          {entry.targetValue === null
                            ? <Typography.Text type="secondary" className="canvas-value-mapping-null">SQL NULL</Typography.Text>
                            : <TypedLiteralInput
                              dataType={dataType}
                              value={entry.targetValue}
                              showTypeLabel={false}
                              placeholder="输入目标值"
                              onChange={(targetValue) => updateRule({
                                ...rule,
                                entries: rule.entries.map((candidate, index) => (
                                  index === entryIndex
                                    ? { ...candidate, targetValue }
                                    : candidate
                                )),
                              })}
                            />}
                          <Button
                            type="text"
                            danger
                            size="small"
                            icon={<DeleteOutlined />}
                            aria-label={`删除映射项 ${entryIndex + 1}`}
                            onClick={() => updateRule({
                              ...rule,
                              entries: rule.entries.filter(
                                (_, index) => index !== entryIndex,
                              ),
                            })}
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                  <Button
                    block
                    size="small"
                    type="dashed"
                    className="canvas-value-mapping-add-entry"
                    icon={<PlusOutlined />}
                    disabled={
                      rule.entries.length >= CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE
                      || totalEntries >= CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES
                    }
                    onClick={() => updateRule({
                      ...rule,
                      entries: [...rule.entries, {
                        sourceValue: createLiteral(dataType),
                        targetValue: createLiteral(dataType),
                      }],
                    })}
                  >
                    添加映射项
                  </Button>
                  <div className="canvas-value-mapping-unmatched">
                    <Typography.Text strong>未匹配非 NULL 值</Typography.Text>
                    <Select
                      size="small"
                      value={rule.unmatchedStrategy}
                      options={[
                        { value: 'KEEP', label: '保留原值' },
                        { value: 'SET_NULL', label: '设为 SQL NULL' },
                        { value: 'SET_LITERAL', label: '设为固定值' },
                        { value: 'ERROR', label: '运行时报错' },
                      ]}
                      onChange={(unmatchedStrategy: ValueMappingUnmatchedStrategy) => updateRule({
                        ...rule,
                        unmatchedStrategy,
                        unmatchedValue: unmatchedStrategy === 'SET_LITERAL'
                          ? rule.unmatchedValue ?? createLiteral(dataType)
                          : null,
                      })}
                    />
                    {rule.unmatchedStrategy === 'SET_LITERAL'
                      && rule.unmatchedValue !== null && (
                      <TypedLiteralInput
                        dataType={dataType}
                        value={rule.unmatchedValue}
                        showTypeLabel={false}
                        placeholder="输入未匹配时使用的固定值"
                        onChange={(unmatchedValue) => updateRule({
                          ...rule,
                          unmatchedValue,
                        })}
                      />
                    )}
                    {rule.unmatchedStrategy !== 'SET_LITERAL' && (
                      <Typography.Text type="secondary" className="canvas-value-mapping-unmatched-summary">
                        {{
                          KEEP: '未命中的值保持不变',
                          SET_NULL: '未命中的值写为 SQL NULL',
                          ERROR: '存在未命中值时终止节点',
                        }[rule.unmatchedStrategy]}
                      </Typography.Text>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      </section>
      {draftError && <Alert showIcon type="error" title={draftError} />}
    </Space>
  );
};
