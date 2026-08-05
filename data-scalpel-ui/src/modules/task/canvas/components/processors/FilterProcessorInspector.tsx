import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CANVAS_FILTER_MAX_CONDITION_NODES,
  CANVAS_FILTER_MAX_DEPTH,
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasFieldPredicate,
  type CanvasFilterCondition,
  type CanvasFilterGroup,
  type CanvasLiteral,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type FilterConfiguration,
  type FilterOperator,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { validateFilterConditionDraft } from './filterConditionDraft';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface FilterProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'FILTER' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface FilterFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const filterOperatorOptions: Array<{ value: FilterOperator; label: string }> = [
  { value: 'EQUALS', label: '等于' },
  { value: 'NOT_EQUALS', label: '不等于' },
  { value: 'GREATER_THAN', label: '大于' },
  { value: 'GREATER_THAN_OR_EQUALS', label: '大于等于' },
  { value: 'LESS_THAN', label: '小于' },
  { value: 'LESS_THAN_OR_EQUALS', label: '小于等于' },
  { value: 'IN', label: '属于集合' },
  { value: 'NOT_IN', label: '不属于集合' },
  { value: 'IS_NULL', label: '为空' },
  { value: 'IS_NOT_NULL', label: '不为空' },
  { value: 'CONTAINS', label: '包含' },
  { value: 'STARTS_WITH', label: '开头是' },
  { value: 'ENDS_WITH', label: '结尾是' },
];

const literalTypeOptions: Array<{ value: CanvasLiteral['dataType']; label: string }> = [
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
].map((value) => ({ value: value as CanvasLiteral['dataType'], label: value }));

const noValueOperator = (operator: FilterOperator) => (
  operator === 'IS_NULL' || operator === 'IS_NOT_NULL'
);

const multiValueOperator = (operator: FilterOperator) => (
  operator === 'IN' || operator === 'NOT_IN'
);

const defaultLiteral = (dataType: CanvasLiteral['dataType'] = 'STRING'): CanvasLiteral => ({
  dataType: dataType === 'GEOMETRY' ? 'STRING' : dataType,
  value: '',
});

const normalizePredicateValues = (
  predicate: CanvasFieldPredicate,
  operator: FilterOperator,
  dataType?: CanvasLiteral['dataType'],
): CanvasLiteral[] => {
  if (noValueOperator(operator)) return [];
  const fallback = defaultLiteral(dataType ?? predicate.values[0]?.dataType);
  if (multiValueOperator(operator)) return predicate.values.length > 0 ? predicate.values : [fallback];
  return [predicate.values[0] ?? fallback];
};

const countConditions = (condition: CanvasFilterCondition): number => (
  condition.kind === 'PREDICATE'
    ? 1
    : 1 + condition.children.reduce((total, child) => total + countConditions(child), 0)
);

interface ConditionEditorProps {
  condition: CanvasFilterCondition;
  columns: CanvasColumnSchema[];
  depth: number;
  totalNodes: number;
  onChange: (condition: CanvasFilterCondition) => void;
  onRemove?: () => void;
}

const PredicateEditor = ({
  predicate,
  columns,
  onChange,
  onRemove,
}: {
  predicate: CanvasFieldPredicate;
  columns: CanvasColumnSchema[];
  onChange: (condition: CanvasFilterCondition) => void;
  onRemove?: () => void;
}) => {
  const selectedColumn = columns.find((column) => column.name === predicate.columnName);
  const selectedColumnMissing = Boolean(predicate.columnName && !selectedColumn);
  const dataType = predicate.values[0]?.dataType
    ?? (selectedColumn?.fieldType === 'GEOMETRY' ? 'STRING' : selectedColumn?.fieldType)
    ?? 'STRING';
  const values = predicate.values.map((literal) => literal.value ?? '');

  return (
    <Card size="small" className="canvas-filter-predicate">
      <div className="canvas-filter-predicate-grid">
        <Select
          showSearch
          value={predicate.columnName || undefined}
          status={selectedColumnMissing ? 'error' : undefined}
          placeholder="字段"
          optionFilterProp="label"
          options={[
            ...(selectedColumnMissing
              ? [{ value: predicate.columnName, label: `${predicate.columnName}（已失效）`, disabled: true }]
              : []),
            ...columns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${column.fieldType}`,
            })),
          ]}
          onChange={(columnName) => {
            const column = columns.find((candidate) => candidate.name === columnName);
            onChange({
              ...predicate,
              columnName,
              values: normalizePredicateValues(
                predicate,
                predicate.operator,
                column?.fieldType,
              ),
            });
          }}
        />
        <Select
          value={predicate.operator}
          options={filterOperatorOptions}
          onChange={(operator) => onChange({
            ...predicate,
            operator,
            values: normalizePredicateValues(predicate, operator, selectedColumn?.fieldType),
          })}
        />
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          aria-label="删除筛选条件"
          disabled={!onRemove}
          onClick={onRemove}
        />
      </div>
      {!noValueOperator(predicate.operator) && (
        <div className="canvas-filter-value-row">
          <Select
            value={dataType}
            options={literalTypeOptions}
            className="canvas-filter-literal-type"
            onChange={(nextType) => onChange({
              ...predicate,
              values: normalizePredicateValues(predicate, predicate.operator, nextType)
                .map((literal) => ({ ...literal, dataType: nextType })),
            })}
          />
          {multiValueOperator(predicate.operator) ? (
            <Select
              mode="tags"
              value={values}
              tokenSeparators={[',']}
              placeholder="输入多个值，按回车确认"
              className="canvas-filter-literal-value"
              onChange={(nextValues) => onChange({
                ...predicate,
                values: nextValues.map((value) => ({ dataType, value })),
              })}
            />
          ) : (
            <Input
              value={values[0] ?? ''}
              placeholder="输入稳定字符串值"
              className="canvas-filter-literal-value"
              onChange={(event) => onChange({
                ...predicate,
                values: [{ dataType, value: event.target.value }],
              })}
            />
          )}
        </div>
      )}
      {selectedColumnMissing && (
        <Typography.Text type="danger">字段已不在当前来源表中，原值已保留。</Typography.Text>
      )}
    </Card>
  );
};

const ConditionEditor = ({
  condition,
  columns,
  depth,
  totalNodes,
  onChange,
  onRemove,
}: ConditionEditorProps) => {
  if (condition.kind === 'PREDICATE') {
    return (
      <PredicateEditor
        predicate={condition}
        columns={columns}
        onChange={onChange}
        onRemove={onRemove}
      />
    );
  }

  const addPredicate = () => {
    const firstColumn = columns[0];
    const predicate: CanvasFieldPredicate = {
      kind: 'PREDICATE',
      columnName: firstColumn?.name ?? '',
      operator: 'EQUALS',
      values: [defaultLiteral(firstColumn?.fieldType)],
    };
    onChange({ ...condition, children: [...condition.children, predicate] });
  };
  const addGroup = () => {
    const group: CanvasFilterGroup = { kind: 'GROUP', operator: 'AND', children: [] };
    onChange({ ...condition, children: [...condition.children, group] });
  };
  const canAdd = totalNodes < CANVAS_FILTER_MAX_CONDITION_NODES;

  return (
    <Card
      size="small"
      className="canvas-filter-group"
      title={(
        <Space size={6}>
          <Tag color="purple">条件组</Tag>
          <Select
            size="small"
            value={condition.operator}
            options={[
              { value: 'AND', label: '满足全部 AND' },
              { value: 'OR', label: '满足任一 OR' },
            ]}
            onChange={(operator) => onChange({ ...condition, operator })}
          />
        </Space>
      )}
      extra={onRemove ? (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          aria-label="删除条件组"
          onClick={onRemove}
        />
      ) : undefined}
    >
      <Space orientation="vertical" size={8} className="canvas-filter-children">
        {condition.children.map((child, index) => (
          <div className="canvas-filter-child-row" key={index}>
            <ConditionEditor
              condition={child}
              columns={columns}
              depth={depth + 1}
              totalNodes={totalNodes}
              onChange={(nextChild) => {
                const children = [...condition.children];
                children[index] = nextChild;
                onChange({ ...condition, children });
              }}
              onRemove={() => onChange({
                ...condition,
                children: condition.children.filter((_, childIndex) => childIndex !== index),
              })}
            />
            <Space orientation="vertical" size={2} className="canvas-filter-order-controls">
              <Button
                type="text"
                size="small"
                icon={<UpOutlined />}
                aria-label="上移筛选条件"
                disabled={index === 0}
                onClick={() => {
                  const children = [...condition.children];
                  [children[index - 1], children[index]] = [children[index], children[index - 1]];
                  onChange({ ...condition, children });
                }}
              />
              <Button
                type="text"
                size="small"
                icon={<DownOutlined />}
                aria-label="下移筛选条件"
                disabled={index === condition.children.length - 1}
                onClick={() => {
                  const children = [...condition.children];
                  [children[index], children[index + 1]] = [children[index + 1], children[index]];
                  onChange({ ...condition, children });
                }}
              />
            </Space>
          </div>
        ))}
        {condition.children.length === 0 && (
          <Typography.Text type="secondary">条件组为空，请添加条件。</Typography.Text>
        )}
        <Space size={6}>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={!canAdd}
            onClick={addPredicate}
          >
            条件
          </Button>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={!canAdd || depth >= CANVAS_FILTER_MAX_DEPTH}
            onClick={addGroup}
          >
            条件组
          </Button>
        </Space>
      </Space>
    </Card>
  );
};

export const FilterConditionTreeEditor = ({
  condition,
  columns,
  onChange,
}: {
  condition: CanvasFilterCondition;
  columns: CanvasColumnSchema[];
  onChange: (condition: CanvasFilterCondition) => void;
}) => (
  <ConditionEditor
    condition={condition}
    columns={columns}
    depth={1}
    totalNodes={countConditions(condition)}
    onChange={onChange}
  />
);

export const FilterProcessorInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: FilterProcessorInspectorProps) => {
  const [form] = Form.useForm<FilterFormValues>();
  const [condition, setCondition] = useState<CanvasFilterCondition>(() => structuredClone(node.configuration.condition));
  const [conditionError, setConditionError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);

  const updateCondition = (nextCondition: CanvasFilterCondition) => {
    setCondition(nextCondition);
    setConditionError(null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        const draftIssue = validateFilterConditionDraft(condition);
        if (draftIssue) {
          setConditionError(draftIssue);
          return false;
        }
        const configuration: FilterConfiguration = {
          sourceTableName: values.sourceTableName,
          outputTableName: values.outputTableName.trim(),
          condition: structuredClone(condition),
        };
        onApply({ id: node.id, type: CanvasNodeType.Filter, configuration });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [condition, form, node.id, onApply, onDirtyChange]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<FilterFormValues>
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
          help={sourceTableMissing ? '原来源表已不在当前上游数据中，配置已保留。' : undefined}
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
          <Input placeholder="例如 paid_orders" />
        </Form.Item>
      </Form>
      <div className="canvas-filter-editor-heading">
        <Typography.Text strong>筛选条件</Typography.Text>
        <Typography.Text type="secondary">
          {countConditions(condition)} / {CANVAS_FILTER_MAX_CONDITION_NODES} 个节点
        </Typography.Text>
      </div>
      {conditionError && <Alert showIcon type="error" title={conditionError} />}
      <FilterConditionTreeEditor
        condition={condition}
        columns={sourceTable?.columns ?? []}
        onChange={updateCondition}
      />
    </Space>
  );
};
