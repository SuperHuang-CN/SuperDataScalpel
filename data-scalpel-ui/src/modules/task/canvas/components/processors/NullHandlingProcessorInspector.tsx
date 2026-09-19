import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Card, Form, Input, Radio, Select, Space, Tag, Typography } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CANVAS_NULL_HANDLING_MAX_RULES,
  CanvasNodeType,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type NullHandlingConfiguration,
  type NullHandlingRule,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';
import { createLiteral } from './canvasLiteral';
import { TypedLiteralInput } from './TypedLiteralInput';

interface NullHandlingProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'NULL_HANDLING' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface NullHandlingFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const move = <T,>(items: T[], from: number, to: number) => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

export const NullHandlingProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: NullHandlingProcessorInspectorProps) => {
  const [form] = Form.useForm<NullHandlingFormValues>();
  const [rules, setRules] = useState<NullHandlingRule[]>(
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
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);

  const updateRules = (next: NullHandlingRule[]) => {
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
          setDraftError('至少配置一条空值处理规则');

        }
        if (rules.length > CANVAS_NULL_HANDLING_MAX_RULES) {
          setDraftError(`规则不能超过 ${CANVAS_NULL_HANDLING_MAX_RULES} 项`);

        }
        const filled = new Set<string>();
        for (const rule of rules) {
          if (rule.kind === 'DROP_ROW') {
            if (rule.columnNames.length === 0) {
              setDraftError('删除行规则至少选择一个检查字段');

            }
            if (new Set(rule.columnNames).size !== rule.columnNames.length) {
              setDraftError('同一删除行规则的检查字段不能重复');

            }
          } else {
            if (!rule.columnName) {
              setDraftError('固定值填充规则必须选择字段');

            }
            if (!filled.add(rule.columnName)) {
              setDraftError(`字段 ${rule.columnName} 只能配置一次固定值填充`);

            }
            if (rule.value.value === null) {
              setDraftError(`字段 ${rule.columnName} 的填充值不能为空`);

            }
          }
        }
        const configuration: NullHandlingConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          rules: structuredClone(rules),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.NullHandling,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [form, node.id, onApply, onDirtyChange, rules]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<NullHandlingFormValues>
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
          <Input placeholder="例如 orders_cleaned" />
        </Form.Item>
      </Form>

      <section className="canvas-processor-editor-section">
        <div className="canvas-processor-editor-heading">
          <span>
            <Typography.Text strong>处理规则</Typography.Text>
            <Typography.Text type="secondary"> · 按从上到下执行</Typography.Text>
          </span>
          <Space size={4}>
            <Button
              size="small"
              icon={<PlusOutlined />}
              disabled={rules.length >= CANVAS_NULL_HANDLING_MAX_RULES}
              onClick={() => updateRules([...rules, {
                kind: 'DROP_ROW',
                columnNames: [],
                matchMode: 'ANY_NULL',
              }])}
            >
              删除行
            </Button>
            <Button
              size="small"
              icon={<PlusOutlined />}
              disabled={rules.length >= CANVAS_NULL_HANDLING_MAX_RULES}
              onClick={() => {
                const column = columns.find(
                  (candidate) => candidate.fieldType !== 'GEOMETRY'
                    && !rules.some(
                      (rule) => rule.kind === 'FILL_LITERAL'
                        && rule.columnName === candidate.name,
                    ),
                );
                updateRules([...rules, {
                  kind: 'FILL_LITERAL',
                  columnName: column?.name ?? '',
                  value: createLiteral(column?.fieldType ?? 'STRING'),
                }]);
              }}
            >
              固定值填充
            </Button>
          </Space>
        </div>
        {rules.length === 0 && (
          <Typography.Text type="secondary">
            添加删除行或固定值填充规则。
          </Typography.Text>
        )}
        <div className="canvas-processor-rule-list">
          {rules.map((rule, index) => {
            const update = (next: NullHandlingRule) => updateRules(
              rules.map((candidate, itemIndex) => itemIndex === index ? next : candidate),
            );
            const selectedNames = rule.kind === 'DROP_ROW'
              ? rule.columnNames : [rule.columnName];
            const invalidNames = selectedNames.filter(
              (name) => name && !columnNames.has(name),
            );
            const fieldOptions = [
              ...invalidNames.map((name) => ({
                value: name,
                label: `${name}（已失效）`,
                disabled: true,
              })),
              ...columns.map((column) => ({
                value: column.name,
                label: `${column.name} · ${column.fieldType}`,
                disabled: rule.kind === 'FILL_LITERAL'
                  && column.fieldType === 'GEOMETRY',
              })),
            ];
            const eventTimeViolation = rule.kind === 'FILL_LITERAL'
              && executionMode === 'STREAMING'
              && sourceTable?.eventTimeColumn === rule.columnName;
            return (
              <Card
                size="small"
                className={`canvas-processor-rule-card${
                  invalidNames.length > 0 || eventTimeViolation ? ' is-invalid' : ''
                }`}
                key={index}
                title={(
                  <Space>
                    <Tag color="purple">{index + 1}</Tag>
                    <span>{rule.kind === 'DROP_ROW' ? '删除空值行' : '固定值填充'}</span>
                    {invalidNames.length > 0 && <Tag color="error">字段已失效</Tag>}
                  </Space>
                )}
                extra={(
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      aria-label={`上移规则 ${index + 1}`}
                      icon={<UpOutlined />}
                      disabled={index === 0}
                      onClick={() => updateRules(move(rules, index, index - 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      aria-label={`下移规则 ${index + 1}`}
                      icon={<DownOutlined />}
                      disabled={index === rules.length - 1}
                      onClick={() => updateRules(move(rules, index, index + 1))}
                    />
                    <Button
                      type="text"
                      danger
                      size="small"
                      aria-label={`删除规则 ${index + 1}`}
                      icon={<DeleteOutlined />}
                      onClick={() => updateRules(
                        rules.filter((_, itemIndex) => itemIndex !== index),
                      )}
                    />
                  </Space>
                )}
              >
                {rule.kind === 'DROP_ROW' ? (
                  <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                    <Select
                      mode="multiple"
                      showSearch
                      optionFilterProp="label"
                      value={rule.columnNames}
                      placeholder="选择一个或多个检查字段"
                      options={fieldOptions}
                      onChange={(columnNames) => update({ ...rule, columnNames })}
                    />
                    <Radio.Group
                      value={rule.matchMode}
                      options={[
                        { value: 'ANY_NULL', label: '任一字段为空就删除' },
                        { value: 'ALL_NULL', label: '全部字段为空才删除' },
                      ]}
                      onChange={(event) => update({
                        ...rule,
                        matchMode: event.target.value,
                      })}
                    />
                  </Space>
                ) : (
                  <Space orientation="vertical" size={8} style={{ width: '100%' }}>
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={rule.columnName || undefined}
                      placeholder="选择填充字段"
                      options={fieldOptions}
                      onChange={(columnName: string) => {
                        const column = columns.find(
                          (candidate) => candidate.name === columnName,
                        );
                        update({
                          ...rule,
                          columnName,
                          value: createLiteral(
                            column?.fieldType ?? rule.value.dataType,
                            rule.value.value ?? '',
                          ),
                        });
                      }}
                    />
                    <TypedLiteralInput
                      dataType={columns.find(
                        (column) => column.name === rule.columnName,
                      )?.fieldType ?? rule.value.dataType}
                      value={rule.value}
                      status={eventTimeViolation ? 'error' : undefined}
                      onChange={(value) => update({ ...rule, value })}
                    />
                    {eventTimeViolation && (
                      <Typography.Text type="danger">
                        实时任务不能填充事件时间字段。
                      </Typography.Text>
                    )}
                  </Space>
                )}
              </Card>
            );
          })}
        </div>
      </section>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      <Alert
        showIcon
        type="info"
        title="只处理 SQL NULL"
        description="浮点 NaN、空字符串和零值不会被自动当作 NULL；填充值不会显示在节点摘要或运行日志中。"
      />
    </Space>
  );
};
