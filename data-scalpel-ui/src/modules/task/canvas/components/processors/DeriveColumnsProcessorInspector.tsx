import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  SettingOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Card, Empty, Form, Input, Modal, Radio, Select, Space, Switch, Tag, Typography } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
} from 'react';
import {
  CANVAS_EXPRESSION_MAX_CASE_BRANCHES,
  CANVAS_EXPRESSION_MAX_DEPTH,
  CANVAS_EXPRESSION_MAX_DERIVATIONS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasExpression,
  type CanvasLiteral,
  type CanvasRuntimeValue,
  type CanvasTableSchema,
  type ColumnDerivation,
  type DeriveBinaryOperator,
  type DeriveColumnsConfiguration,
  type DeriveColumnsOperation,
  type DeriveFunction,
} from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps } from '../../nodes/nodeSpec';
import {
  ProcessorTablePickerModal,
  type ProcessorOperationDraft,
} from '../../nodes/inspectorAdapter';
import { FilterConditionTreeEditor } from './FilterProcessorInspector';
import {
  createDefaultFilterCondition,
  validateFilterConditionDraft,
} from './filterConditionDraft';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

type DeriveColumnsProcessorInspectorProps = CanvasNodeInspectorComponentProps<typeof CanvasNodeType.DeriveColumns>;

interface DeriveColumnsFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const expressionKindOptions: Array<{ value: CanvasExpression['kind']; label: string }> = [
  { value: 'COLUMN', label: '字段引用' },
  { value: 'LITERAL', label: '固定值' },
  { value: 'RUNTIME_VALUE', label: '运行时变量' },
  { value: 'BINARY', label: '二元运算' },
  { value: 'FUNCTION', label: '函数' },
  { value: 'CASE_WHEN', label: '条件 CASE' },
];

const runtimeValueOptions: Array<{
  value: CanvasRuntimeValue;
  label: string;
  type: 'STRING' | 'TIMESTAMP';
  help: string;
}> = [
  {
    value: 'EXECUTION_ID',
    label: '本次执行 Attempt ID',
    type: 'STRING',
    help: '每次实际执行唯一，同一次 Attempt 内固定。',
  },
  {
    value: 'EXECUTION_STARTED_AT',
    label: '本次执行开始时间',
    type: 'TIMESTAMP',
    help: '同一次 Attempt 内固定，不是逐行当前时间。',
  },
];

const binaryOperatorOptions: Array<{ value: DeriveBinaryOperator; label: string }> = [
  { value: 'ADD', label: '加 +' },
  { value: 'SUBTRACT', label: '减 −' },
  { value: 'MULTIPLY', label: '乘 ×' },
  { value: 'DIVIDE', label: '除 ÷' },
  { value: 'MODULO', label: '取模 %' },
];

const functionOptions: Array<{ value: DeriveFunction; label: string }> = [
  { value: 'TRIM', label: 'TRIM · 去除两端空白' },
  { value: 'LTRIM', label: 'LTRIM · 去除左侧空白' },
  { value: 'RTRIM', label: 'RTRIM · 去除右侧空白' },
  { value: 'LOWER', label: 'LOWER · 转为小写' },
  { value: 'UPPER', label: 'UPPER · 转为大写' },
  { value: 'REPLACE', label: 'REPLACE · 替换文本' },
  { value: 'SUBSTRING', label: 'SUBSTRING · 截取文本' },
  { value: 'COALESCE', label: 'COALESCE · 首个非空值' },
  { value: 'CONCAT', label: 'CONCAT · 拼接文本' },
  { value: 'DATE_FORMAT', label: 'DATE_FORMAT · 格式化日期' },
  { value: 'DATE_ADD', label: 'DATE_ADD · 增加天数' },
  { value: 'DATE_SUB', label: 'DATE_SUB · 减少天数' },
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

const literalExpression = (
  dataType: CanvasLiteral['dataType'] = 'STRING',
  value = '',
): CanvasExpression => ({
  kind: 'LITERAL',
  literal: {
    dataType: dataType === 'GEOMETRY' ? 'STRING' : dataType,
    value,
  },
});

const columnExpression = (columns: CanvasColumnSchema[]): CanvasExpression => ({
  kind: 'COLUMN',
  columnName: columns[0]?.name ?? '',
});

const defaultFunctionArguments = (
  functionName: DeriveFunction,
  columns: CanvasColumnSchema[],
): CanvasExpression[] => {
  const column = () => columnExpression(columns);
  switch (functionName) {
    case 'TRIM':
    case 'LTRIM':
    case 'RTRIM':
    case 'LOWER':
    case 'UPPER':
      return [column()];
    case 'REPLACE':
      return [column(), literalExpression('STRING'), literalExpression('STRING')];
    case 'SUBSTRING':
      return [column(), literalExpression('INTEGER', '1'), literalExpression('INTEGER', '1')];
    case 'COALESCE':
    case 'CONCAT':
      return [column(), literalExpression('STRING')];
    case 'DATE_FORMAT':
      return [column(), literalExpression('STRING', 'yyyy-MM-dd')];
    case 'DATE_ADD':
    case 'DATE_SUB':
      return [column(), literalExpression('INTEGER', '1')];
  }
};

const defaultExpression = (
  kind: CanvasExpression['kind'],
  columns: CanvasColumnSchema[],
): CanvasExpression => {
  switch (kind) {
    case 'COLUMN':
      return columnExpression(columns);
    case 'LITERAL':
      return literalExpression();
    case 'RUNTIME_VALUE':
      return { kind: 'RUNTIME_VALUE', value: 'EXECUTION_ID' };
    case 'BINARY':
      return {
        kind: 'BINARY',
        operator: 'ADD',
        left: columnExpression(columns),
        right: literalExpression('INTEGER', '0'),
      };
    case 'FUNCTION':
      return {
        kind: 'FUNCTION',
        function: 'TRIM',
        arguments: defaultFunctionArguments('TRIM', columns),
      };
    case 'CASE_WHEN':
      return {
        kind: 'CASE_WHEN',
        branches: [{
          condition: createDefaultFilterCondition(columns[0]),
          result: columnExpression(columns),
        }],
        elseExpression: null,
      };
  }
};

const expressionSummary = (expression: CanvasExpression): string => {
  switch (expression.kind) {
    case 'COLUMN':
      return expression.columnName || '未选择字段';
    case 'LITERAL':
      return `<${expression.literal.dataType}>`;
    case 'RUNTIME_VALUE':
      return `$${expression.value}`;
    case 'BINARY':
      return `${expressionSummary(expression.left)} ${binaryOperatorOptions.find((option) => option.value === expression.operator)?.label.slice(-1) ?? expression.operator} ${expressionSummary(expression.right)}`;
    case 'FUNCTION':
      return `${expression.function}(${expression.arguments.map(expressionSummary).join(', ')})`;
    case 'CASE_WHEN':
      return `CASE(${expression.branches.length} 个分支)`;
  }
};

const expressionNodeCount = (expression: CanvasExpression): number => {
  switch (expression.kind) {
    case 'COLUMN':
    case 'LITERAL':
    case 'RUNTIME_VALUE':
      return 1;
    case 'BINARY':
      return 1 + expressionNodeCount(expression.left) + expressionNodeCount(expression.right);
    case 'FUNCTION':
      return 1 + expression.arguments.reduce(
        (count, argument) => count + expressionNodeCount(argument),
        0,
      );
    case 'CASE_WHEN':
      return 1 + expression.branches.reduce(
        (count, branch) => count + expressionNodeCount(branch.result),
        0,
      ) + (expression.elseExpression ? expressionNodeCount(expression.elseExpression) : 0);
  }
};

const validateExpressionDraft = (expression: CanvasExpression): string | null => {
  switch (expression.kind) {
    case 'COLUMN':
      return expression.columnName ? null : '表达式中存在未选择的字段引用';
    case 'LITERAL':
      return expression.literal.value === null ? '表达式中存在未填写的 Literal' : null;
    case 'RUNTIME_VALUE':
      return runtimeValueOptions.some((option) => option.value === expression.value)
        ? null : '表达式中存在不受支持的运行时变量';
    case 'BINARY':
      return validateExpressionDraft(expression.left)
        ?? validateExpressionDraft(expression.right);
    case 'FUNCTION': {
      if (expression.arguments.length === 0) return `${expression.function} 缺少参数`;
      for (const argument of expression.arguments) {
        const issue = validateExpressionDraft(argument);
        if (issue) return issue;
      }
      return null;
    }
    case 'CASE_WHEN':
      if (expression.branches.length === 0) return 'CASE_WHEN 至少需要一个分支';
      for (const branch of expression.branches) {
        const conditionIssue = validateFilterConditionDraft(branch.condition);
        if (conditionIssue) return conditionIssue;
        const resultIssue = validateExpressionDraft(branch.result);
        if (resultIssue) return resultIssue;
      }
      return expression.elseExpression
        ? validateExpressionDraft(expression.elseExpression)
        : null;
  }
};

interface ExpressionEditorProps {
  expression: CanvasExpression;
  columns: CanvasColumnSchema[];
  depth: number;
  executionMode: 'BATCH' | 'STREAMING';
  onChange: (expression: CanvasExpression) => void;
}

const ExpressionEditor = ({
  expression,
  columns,
  depth,
  executionMode,
  onChange,
}: ExpressionEditorProps) => {
  const nestedDisabled = depth >= CANVAS_EXPRESSION_MAX_DEPTH;
  const kindOptions = expressionKindOptions.map((option) => ({
    ...option,
    disabled: nestedDisabled
      && option.value !== 'COLUMN'
      && option.value !== 'LITERAL'
      && option.value !== 'RUNTIME_VALUE',
  }));
  const kindSelect = (
    <Select
      size="small"
      className="canvas-expression-kind-select"
      value={expression.kind}
      options={kindOptions}
      popupMatchSelectWidth={240}
      onChange={(kind) => onChange(defaultExpression(kind, columns))}
    />
  );
  const nodeClassName = `canvas-expression-node${depth > 1 ? ' is-nested' : ''}`;

  if (expression.kind === 'COLUMN') {
    const missing = Boolean(
      expression.columnName
      && !columns.some((column) => column.name === expression.columnName),
    );
    return (
      <div className={nodeClassName}>
        <div className="canvas-expression-node-row">
          {kindSelect}
        <Select
          size="small"
          className="canvas-expression-value-select"
          showSearch
          optionFilterProp="label"
          popupMatchSelectWidth={320}
          status={missing ? 'error' : undefined}
          value={expression.columnName || undefined}
          placeholder="选择来源字段"
          options={[
            ...(missing ? [{
              value: expression.columnName,
              label: `${expression.columnName}（已失效）`,
              disabled: true,
            }] : []),
            ...columns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${column.fieldType}`,
            })),
          ]}
          onChange={(columnName) => onChange({ ...expression, columnName })}
        />
        </div>
        {missing && (
          <Typography.Text className="canvas-expression-node-help" type="danger">
            原字段已失效，引用值已保留。
          </Typography.Text>
        )}
      </div>
    );
  }

  if (expression.kind === 'LITERAL') {
    return (
      <div className={nodeClassName}>
        <div className="canvas-expression-node-row">
          {kindSelect}
          <Space.Compact className="canvas-expression-literal-editor">
          <Select
            size="small"
            className="canvas-expression-literal-type"
            value={expression.literal.dataType}
            options={literalTypeOptions}
            popupMatchSelectWidth={180}
            onChange={(dataType) => onChange({
              ...expression,
              literal: { dataType, value: expression.literal.value ?? '' },
            })}
          />
          <Input
            size="small"
            autoComplete="off"
            value={expression.literal.value ?? ''}
            placeholder="稳定字符串值"
            onChange={(event) => onChange({
              ...expression,
              literal: { ...expression.literal, value: event.target.value },
            })}
          />
        </Space.Compact>
        </div>
      </div>
    );
  }

  if (expression.kind === 'RUNTIME_VALUE') {
    const selected = runtimeValueOptions.find((option) => option.value === expression.value);
    return (
      <div className={nodeClassName}>
        <div className="canvas-expression-node-row">
          {kindSelect}
          <Select
            size="small"
            className="canvas-expression-value-select"
            value={expression.value}
            popupMatchSelectWidth={320}
            options={runtimeValueOptions.map((option) => ({
              value: option.value,
              label: `${option.label} · ${option.type}`,
            }))}
            onChange={(value: CanvasRuntimeValue) => onChange({
              kind: 'RUNTIME_VALUE',
              value,
            })}
          />
        </div>
        <div className="canvas-expression-node-help">
          <Typography.Text type="secondary">由执行引擎注入；设计态只分析类型。{selected?.help}</Typography.Text>
          {executionMode === 'STREAMING' && (
            <Typography.Text type="secondary">
              流任务中在整个 Attempt 内固定，不是微批时间。
            </Typography.Text>
          )}
        </div>
      </div>
    );
  }

  if (expression.kind === 'BINARY') {
    return (
      <div className={nodeClassName}>
        <div className="canvas-expression-node-row">
          {kindSelect}
        <Select
          size="small"
          className="canvas-expression-value-select"
          value={expression.operator}
          options={binaryOperatorOptions}
          popupMatchSelectWidth={220}
          onChange={(operator) => onChange({ ...expression, operator })}
        />
        </div>
        <div className="canvas-expression-children">
          <div className="canvas-expression-argument">
            <Typography.Text type="secondary">左值</Typography.Text>
            <ExpressionEditor
              expression={expression.left}
              columns={columns}
              depth={depth + 1}
              executionMode={executionMode}
              onChange={(left) => onChange({ ...expression, left })}
            />
          </div>
          <div className="canvas-expression-argument">
            <Typography.Text type="secondary">右值</Typography.Text>
            <ExpressionEditor
              expression={expression.right}
              columns={columns}
              depth={depth + 1}
              executionMode={executionMode}
              onChange={(right) => onChange({ ...expression, right })}
            />
          </div>
        </div>
      </div>
    );
  }

  if (expression.kind === 'FUNCTION') {
    const variableArguments = expression.function === 'COALESCE'
      || expression.function === 'CONCAT';
    return (
      <div className={nodeClassName}>
        <div className="canvas-expression-node-row">
          {kindSelect}
          <Select
            size="small"
            showSearch
            optionFilterProp="label"
            className="canvas-expression-value-select"
            value={expression.function}
            options={functionOptions}
            popupMatchSelectWidth={300}
            onChange={(functionName) => onChange({
              ...expression,
              function: functionName,
              arguments: defaultFunctionArguments(functionName, columns),
            })}
          />
          {variableArguments && (
            <Button
              size="small"
              icon={<PlusOutlined />}
              onClick={() => onChange({
                ...expression,
                arguments: [...expression.arguments, columnExpression(columns)],
              })}
            >
              添加参数
            </Button>
          )}
        </div>
        <div className="canvas-expression-children">
          {expression.arguments.map((argument, index) => (
            <div className="canvas-expression-argument" key={index}>
              <Typography.Text type="secondary">参数 {index + 1}</Typography.Text>
              <ExpressionEditor
                expression={argument}
                columns={columns}
                depth={depth + 1}
                executionMode={executionMode}
                onChange={(nextArgument) => {
                  const argumentsCopy = [...expression.arguments];
                  argumentsCopy[index] = nextArgument;
                  onChange({ ...expression, arguments: argumentsCopy });
                }}
              />
              {variableArguments && expression.arguments.length > 2 && (
                <Button
                  className="canvas-expression-remove-argument"
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label={`删除参数 ${index + 1}`}
                  onClick={() => onChange({
                    ...expression,
                    arguments: expression.arguments.filter(
                      (_, argumentIndex) => argumentIndex !== index,
                    ),
                  })}
                />
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={nodeClassName}>
      <div className="canvas-expression-node-row">
        {kindSelect}
        <Typography.Text type="secondary">{expression.branches.length} 个 WHEN 分支</Typography.Text>
      </div>
      <Space orientation="vertical" size={8} className="canvas-expression-case">
        {expression.branches.map((branch, index) => (
          <div
            key={index}
            className="canvas-expression-case-branch"
          >
            <div className="canvas-expression-case-branch-heading">
              <Typography.Text strong>WHEN {index + 1}</Typography.Text>
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                disabled={expression.branches.length === 1}
                aria-label={`删除 CASE 分支 ${index + 1}`}
                onClick={() => onChange({
                  ...expression,
                  branches: expression.branches.filter(
                    (_, branchIndex) => branchIndex !== index,
                  ),
                })}
              />
            </div>
            <Typography.Text type="secondary">条件</Typography.Text>
            <FilterConditionTreeEditor
              condition={branch.condition}
              columns={columns}
              onChange={(condition) => {
                const branches = [...expression.branches];
                branches[index] = { ...branch, condition };
                onChange({ ...expression, branches });
              }}
            />
            <Typography.Text type="secondary">结果</Typography.Text>
            <ExpressionEditor
              expression={branch.result}
              columns={columns}
              depth={depth + 1}
              executionMode={executionMode}
              onChange={(result) => {
                const branches = [...expression.branches];
                branches[index] = { ...branch, result };
                onChange({ ...expression, branches });
              }}
            />
          </div>
        ))}
        <Button
          size="small"
          icon={<PlusOutlined />}
          disabled={expression.branches.length >= CANVAS_EXPRESSION_MAX_CASE_BRANCHES}
          onClick={() => onChange({
            ...expression,
            branches: [...expression.branches, {
              condition: createDefaultFilterCondition(columns[0]),
              result: columnExpression(columns),
            }],
          })}
        >
          CASE 分支
        </Button>
        <div className="canvas-expression-else-heading">
          <Typography.Text>ELSE</Typography.Text>
          <Switch
            size="small"
            checked={expression.elseExpression !== null}
            onChange={(checked) => onChange({
              ...expression,
              elseExpression: checked ? columnExpression(columns) : null,
            })}
          />
        </div>
        {expression.elseExpression && (
          <ExpressionEditor
            expression={expression.elseExpression}
            columns={columns}
            depth={depth + 1}
            executionMode={executionMode}
            onChange={(elseExpression) => onChange({ ...expression, elseExpression })}
          />
        )}
      </Space>
    </div>
  );
};

const automaticDerivationStatus = (
  derivation: ColumnDerivation,
  columns: readonly CanvasColumnSchema[] | undefined,
): string => {
  if (!derivation.targetColumnName.trim()) return '自动 · 等待目标字段名';
  if (!columns) return '自动 · 待判断';
  return columns.some((column) => column.name === derivation.targetColumnName)
    ? '自动 · 覆盖已有字段'
    : '自动 · 新增字段';
};

export const LegacyDeriveColumnsProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: DeriveColumnsProcessorInspectorProps) => {
  const [form] = Form.useForm<DeriveColumnsFormValues>();
  const [derivations, setDerivations] = useState<ColumnDerivation[]>(
    () => structuredClone(node.configuration.derivations),
  );
  const [selectedIndex, setSelectedIndex] = useState<number | null>(
    node.configuration.derivations.length > 0 ? 0 : null,
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const tableOptions = useMemo(() => [
    ...(sourceTableMissing
      ? [{ value: sourceTableName, label: `${sourceTableName}（已失效）`, disabled: true }]
      : []),
    ...inputTables.map((table) => ({
      value: table.name,
      label: `${table.name} · ${table.columns.length} 字段`,
    })),
  ], [inputTables, sourceTableMissing, sourceTableName]);
  const selectedDerivation = selectedIndex === null ? null : derivations[selectedIndex] ?? null;

  const updateDerivations = (
    nextDerivations: ColumnDerivation[],
    nextSelectedIndex = selectedIndex,
  ) => {
    setDerivations(nextDerivations);
    setSelectedIndex(nextSelectedIndex);
    setDraftError(nextDerivations.length === 0 ? '至少配置一个派生字段' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (derivations.length === 0) {
          setDraftError('至少配置一个派生字段');

        }
        const targets = new Set<string>();
        for (const derivation of derivations) {
          if (!derivation.targetColumnName.trim()) {
            setDraftError('派生字段中存在未填写的目标字段名');

          }
          if (!targets.add(derivation.targetColumnName.trim())) {
            setDraftError(`目标字段重复配置：${derivation.targetColumnName.trim()}`);

          }
          const expressionIssue = validateExpressionDraft(derivation.expression);
          if (expressionIssue) {
            setDraftError(expressionIssue);

          }
        }
        const configuration: DeriveColumnsConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          derivations: structuredClone(derivations),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.DeriveColumns,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [derivations, form, node.id, onApply, onDirtyChange]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<DeriveColumnsFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，配置和表达式引用均已保留。' : undefined}
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
          <Input placeholder="例如 orders_enriched" />
        </Form.Item>
      </Form>

      <div className="canvas-derive-heading">
        <Typography.Text strong>派生字段</Typography.Text>
        <Button
          size="small"
          icon={<PlusOutlined />}
          disabled={derivations.length >= CANVAS_EXPRESSION_MAX_DERIVATIONS}
          onClick={() => {
            const nextDerivations = [...derivations, {
              targetColumnName: '',
              expression: columnExpression(sourceColumns),
            }];
            updateDerivations(nextDerivations, nextDerivations.length - 1);
          }}
        >
          添加
        </Button>
      </div>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      <div className="canvas-derive-list">
        {derivations.length === 0 && (
          <Typography.Text type="secondary">尚未配置派生字段。</Typography.Text>
        )}
        {derivations.map((derivation, index) => (
          <div
            role="button"
            tabIndex={0}
            className={`canvas-derive-list-item${selectedIndex === index ? ' is-active' : ''}`}
            key={index}
            onClick={() => setSelectedIndex(index)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                setSelectedIndex(index);
              }
            }}
          >
            <div className="canvas-derive-list-meta">
              <Typography.Text ellipsis>
                {derivation.targetColumnName || `未命名字段 ${index + 1}`}
              </Typography.Text>
              <Typography.Text type="secondary">
                {automaticDerivationStatus(derivation, sourceTable?.columns)} · {expressionSummary(derivation.expression)}
              </Typography.Text>
            </div>
            <Space size={0}>
              <Button
                type="text"
                size="small"
                icon={<UpOutlined />}
                disabled={index === 0}
                onClick={(event) => {
                  event.stopPropagation();
                  const nextDerivations = [...derivations];
                  [nextDerivations[index - 1], nextDerivations[index]] = [
                    nextDerivations[index],
                    nextDerivations[index - 1],
                  ];
                  updateDerivations(
                    nextDerivations,
                    selectedIndex === index
                      ? index - 1
                      : selectedIndex === index - 1 ? index : selectedIndex,
                  );
                }}
              />
              <Button
                type="text"
                size="small"
                icon={<DownOutlined />}
                disabled={index === derivations.length - 1}
                onClick={(event) => {
                  event.stopPropagation();
                  const nextDerivations = [...derivations];
                  [nextDerivations[index], nextDerivations[index + 1]] = [
                    nextDerivations[index + 1],
                    nextDerivations[index],
                  ];
                  updateDerivations(
                    nextDerivations,
                    selectedIndex === index
                      ? index + 1
                      : selectedIndex === index + 1 ? index : selectedIndex,
                  );
                }}
              />
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={(event) => {
                  event.stopPropagation();
                  const nextDerivations = derivations.filter(
                    (_, derivationIndex) => derivationIndex !== index,
                  );
                  const nextSelection = nextDerivations.length === 0
                    ? null
                    : Math.min(index, nextDerivations.length - 1);
                  updateDerivations(nextDerivations, nextSelection);
                }}
              />
            </Space>
          </div>
        ))}
      </div>

      {selectedDerivation && selectedIndex !== null && (
        <Card
          size="small"
          className="canvas-derive-editor"
          title={`编辑派生字段 ${selectedIndex + 1}`}
          extra={(
            <Tag>
              {expressionNodeCount(selectedDerivation.expression)} 个表达式节点
            </Tag>
          )}
        >
          <Space orientation="vertical" size={10} className="canvas-expression-editor">
            <Input
              autoComplete="off"
              value={selectedDerivation.targetColumnName}
              placeholder="目标字段名"
              status={!selectedDerivation.targetColumnName ? 'error' : undefined}
              onChange={(event) => {
                const nextDerivations = [...derivations];
                nextDerivations[selectedIndex] = {
                  ...selectedDerivation,
                  targetColumnName: event.target.value,
                };
                updateDerivations(nextDerivations);
              }}
            />
            <Typography.Text type="secondary">
              {automaticDerivationStatus(selectedDerivation, sourceTable?.columns)}
            </Typography.Text>
            {executionMode === 'STREAMING'
              && sourceColumns.some((column) => column.name === selectedDerivation.targetColumnName)
              && selectedDerivation.targetColumnName === sourceTable?.eventTimeColumn && (
              <Alert showIcon type="error" title="流模式不能覆盖事件时间字段" />
            )}
            <ExpressionEditor
              expression={selectedDerivation.expression}
              columns={sourceColumns}
              depth={1}
              executionMode={executionMode}
              onChange={(expression) => {
                const nextDerivations = [...derivations];
                nextDerivations[selectedIndex] = {
                  ...selectedDerivation,
                  expression,
                };
                updateDerivations(nextDerivations);
              }}
            />
          </Space>
        </Card>
      )}
    </Space>
  );
};

type RuleSelection = { scope: 'GLOBAL' | 'LOCAL'; index: number } | null;

const normalizedConfiguration = (configuration: DeriveColumnsConfiguration): Required<Pick<DeriveColumnsConfiguration, 'globalDerivations' | 'operations'>> => ({
  globalDerivations: structuredClone(configuration.globalDerivations ?? []),
  operations: structuredClone(configuration.operations ?? []),
});

const movedRuleSelection = (
  selection: RuleSelection,
  scope: 'GLOBAL' | 'LOCAL',
  index: number,
  offset: -1 | 1,
): RuleSelection => {
  if (selection?.scope !== scope) return selection;
  const target = index + offset;
  if (selection.index === index) return { scope, index: target };
  if (selection.index === target) return { scope, index };
  return selection;
};

const commonColumns = (
  operations: readonly DeriveColumnsOperation[],
  inputTables: readonly CanvasTableSchema[],
): CanvasColumnSchema[] => {
  const selectedTables = operations
    .map((operation) => inputTables.find((table) => table.name === operation.sourceTableName))
    .filter((table): table is CanvasTableSchema => Boolean(table));
  if (selectedTables.length === 0) return [];
  return selectedTables[0].columns.filter((column) => selectedTables.every((table) => (
    table.columns.some((candidate) => candidate.name === column.name)
  )));
};

const globalDerivationStatus = (
  derivation: ColumnDerivation,
  operations: readonly DeriveColumnsOperation[],
  inputTables: readonly CanvasTableSchema[],
): string => {
  if (!derivation.targetColumnName.trim()) return '自动 · 等待目标字段名';
  let existingTargetCount = 0;
  let addCount = 0;
  let pendingCount = 0;
  for (const operation of operations) {
    const table = inputTables.find((candidate) => candidate.name === operation.sourceTableName);
    if (!table) {
      pendingCount++;
    } else if (table.columns.some((column) => column.name === derivation.targetColumnName)) {
      existingTargetCount++;
    } else {
      addCount++;
    }
  }
  const states = [`自动 · 覆盖 ${existingTargetCount} 张`, `新增 ${addCount} 张`];
  if (pendingCount > 0) states.push(`待判断 ${pendingCount} 张`);
  return states.join(' · ');
};

interface DerivationRuleEditorProps {
  derivation: ColumnDerivation | null;
  columns: CanvasColumnSchema[];
  writeStatus: string;
  executionMode: 'BATCH' | 'STREAMING';
  eventTimeColumn: string | null;
  readOnly?: boolean;
  onChange: (derivation: ColumnDerivation) => void;
  onEditGlobal?: () => void;
}

const DerivationRuleEditor = ({
  derivation,
  columns,
  writeStatus,
  executionMode,
  eventTimeColumn,
  readOnly = false,
  onChange,
  onEditGlobal,
}: DerivationRuleEditorProps) => {
  if (!derivation) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="从左侧选择或添加一条规则" />;
  if (readOnly) {
    return <Card size="small" title="全局规则详情" extra={<Tag>只读</Tag>}>
      <Space orientation="vertical" size={12} className="canvas-expression-editor">
        <div><Typography.Text type="secondary">目标字段</Typography.Text><Typography.Text code>{derivation.targetColumnName || '未命名字段'}</Typography.Text></div>
        <div><Typography.Text type="secondary">字段写入</Typography.Text><Typography.Text>{writeStatus}</Typography.Text></div>
        <div><Typography.Text type="secondary">表达式</Typography.Text><Typography.Text>{expressionSummary(derivation.expression)}</Typography.Text></div>
        <Button onClick={onEditGlobal}>前往全局配置</Button>
      </Space>
    </Card>;
  }
  const targetExists = columns.some((column) => column.name === derivation.targetColumnName);
  return <Card
    size="small"
    className="canvas-derive-editor"
    title="规则详细配置"
    extra={<Tag>{expressionNodeCount(derivation.expression)} 个表达式节点</Tag>}
  >
    <Space orientation="vertical" size={10} className="canvas-expression-editor">
      <div className="canvas-derive-rule-field">
        <Typography.Text strong>目标字段 code</Typography.Text>
        <Input
          autoComplete="off"
          value={derivation.targetColumnName}
          placeholder="例如 source_record_hash"
          onChange={(event) => onChange({ ...derivation, targetColumnName: event.target.value })}
        />
        <Typography.Text type="secondary">{writeStatus}</Typography.Text>
      </div>
      {executionMode === 'STREAMING' && targetExists
        && derivation.targetColumnName === eventTimeColumn && (
        <Alert showIcon type="error" title="流模式不能覆盖事件时间字段" />
      )}
      <div className="canvas-expression-preview">
        <Typography.Text type="secondary">表达式预览</Typography.Text>
        <Typography.Text code ellipsis>
          {(derivation.targetColumnName || '目标字段')} = {expressionSummary(derivation.expression)}
        </Typography.Text>
      </div>
      <ExpressionEditor
        expression={derivation.expression}
        columns={columns}
        depth={1}
        executionMode={executionMode}
        onChange={(expression) => onChange({ ...derivation, expression })}
      />
    </Space>
  </Card>;
};

interface RuleListProps {
  title: string;
  rules: readonly ColumnDerivation[];
  selection: RuleSelection;
  scope: 'GLOBAL' | 'LOCAL';
  readOnly?: boolean;
  onSelect: (selection: RuleSelection) => void;
  onMove?: (index: number, offset: -1 | 1) => void;
  onRemove?: (index: number) => void;
  onAdd?: () => void;
}

const RuleList = ({
  title, rules, selection, scope, readOnly = false, onSelect, onMove, onRemove, onAdd,
}: RuleListProps) => <section className="canvas-derive-rule-section">
  <div className="canvas-derive-rule-section-heading">
    <Typography.Text strong>{title}</Typography.Text>
    {!readOnly && onAdd && <Button size="small" icon={<PlusOutlined />} onClick={onAdd}>添加</Button>}
  </div>
  {rules.length === 0 ? <Typography.Text type="secondary">暂无规则</Typography.Text> : rules.map((rule, index) => (
    <div
      className={`canvas-derive-rule-row${selection?.scope === scope && selection.index === index ? ' is-active' : ''}`}
      key={`${scope}-${index}`}
      role="button"
      tabIndex={0}
      onClick={() => onSelect({ scope, index })}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onSelect({ scope, index });
        }
      }}
    >
      <div className="canvas-derive-rule-row-main">
        <Typography.Text ellipsis>{rule.targetColumnName || `未命名字段 ${index + 1}`}</Typography.Text>
        <Typography.Text type="secondary" ellipsis>{expressionSummary(rule.expression)}</Typography.Text>
      </div>
      {!readOnly && <Space size={0} onClick={(event) => event.stopPropagation()}>
        <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0} onClick={() => onMove?.(index, -1)} />
        <Button type="text" size="small" icon={<DownOutlined />} disabled={index === rules.length - 1} onClick={() => onMove?.(index, 1)} />
        <Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => onRemove?.(index)} />
      </Space>}
    </div>
  ))}
</section>;

export const DeriveColumnsProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: DeriveColumnsProcessorInspectorProps) => {
  const initial = useMemo(() => normalizedConfiguration(node.configuration), [node.configuration]);
  const [configuration, setConfiguration] = useState(initial);
  const [managerOpen, setManagerOpen] = useState(false);
  const [globalOpen, setGlobalOpen] = useState(false);
  const [globalDraft, setGlobalDraft] = useState<ColumnDerivation[] | null>(null);
  const [tableDraft, setTableDraft] = useState<DeriveColumnsOperation | null>(null);
  const [globalSelection, setGlobalSelection] = useState<RuleSelection>(initial.globalDerivations.length ? { scope: 'GLOBAL', index: 0 } : null);
  const [tableSelection, setTableSelection] = useState<RuleSelection>(null);
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const activeOperation = tableDraft;
  const globalRules = globalDraft ?? configuration.globalDerivations;
  const activeTable = activeOperation
    ? inputTables.find((table) => table.name === activeOperation.sourceTableName)
    : undefined;
  const globalColumns = useMemo(
    () => commonColumns(configuration.operations, inputTables),
    [configuration.operations, inputTables],
  );

  const updateConfiguration = (next: typeof configuration) => {
    setConfiguration(next);
    onDirtyChange(true);
  };
  const moveRule = (rules: readonly ColumnDerivation[], index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= rules.length) return [...rules];
    const next = [...rules];
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  };
  const defaultRule = (columns: CanvasColumnSchema[]): ColumnDerivation => ({
    targetColumnName: '', expression: columnExpression(columns),
  });
  const updateActiveOperation = (nextOperation: DeriveColumnsOperation) => setTableDraft(nextOperation);

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      onApply({
        id: node.id,
        type: CanvasNodeType.DeriveColumns,
        configuration: {
          globalDerivations: structuredClone(configuration.globalDerivations),
          operations: structuredClone(configuration.operations),
        } as DeriveColumnsConfiguration,
      });
      onDirtyChange(false);
      return true;
    },
  }), [configuration, node.id, onApply, onDirtyChange]);

  const selectedGlobal = globalSelection?.scope === 'GLOBAL'
    ? globalRules[globalSelection.index] ?? null : null;
  const selectedLocal = tableSelection?.scope === 'LOCAL' && activeOperation
    ? activeOperation.derivations[tableSelection.index] ?? null : null;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <ProcessorValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <div className="canvas-derive-compact-heading">
      <div><Typography.Text strong>全局配置</Typography.Text><Typography.Text type="secondary"> · {configuration.globalDerivations.length} 条规则 · 作用于 {configuration.operations.length} 张表</Typography.Text></div>
      <Button size="small" icon={<SettingOutlined />} onClick={() => {
        setGlobalDraft(structuredClone(configuration.globalDerivations));
        setGlobalSelection(configuration.globalDerivations.length ? { scope: 'GLOBAL', index: 0 } : null);
        setGlobalOpen(true);
      }}>配置</Button>
    </div>
    <div className="canvas-derive-compact-heading">
      <div><Typography.Text strong>处理表</Typography.Text><Typography.Text type="secondary"> · 每张表独立配置规则</Typography.Text></div>
      <Button size="small" icon={<SettingOutlined />} onClick={() => setManagerOpen(true)}>管理处理表</Button>
    </div>
    <div className="canvas-derive-operation-list">
      {configuration.operations.length === 0 ? <Typography.Text type="secondary">尚未选择处理表。</Typography.Text> : configuration.operations.map((operation, index) => {
        const tableAvailable = inputTables.some((table) => table.name === operation.sourceTableName);
        return <div className={`canvas-derive-operation-row${tableAvailable ? '' : ' is-invalid'}`} key={operation.operationId}>
          <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
          <div><Typography.Text ellipsis>{operation.sourceTableName}</Typography.Text><Typography.Text type={tableAvailable ? 'secondary' : 'danger'} ellipsis>{tableAvailable ? `全局 ${configuration.globalDerivations.length} 条 · 本表 ${operation.derivations.length} 条` : '上游表已失效'}</Typography.Text></div>
          <Button type="text" size="small" icon={<SettingOutlined />} aria-label={`配置 ${operation.sourceTableName}`} onClick={() => {
            setTableDraft(structuredClone(operation));
            setTableSelection(operation.derivations.length ? { scope: 'LOCAL', index: 0 } : configuration.globalDerivations.length ? { scope: 'GLOBAL', index: 0 } : null);
          }} />
        </div>;
      })}
    </div>

    <Modal open={globalOpen} width={1040} className="canvas-derive-rules-modal" title="配置全局派生规则"
      styles={{ body: { overflow: 'hidden' } }} destroyOnHidden
      onCancel={() => { setGlobalDraft(null); setGlobalOpen(false); }} okText="保存全局规则" onOk={() => {
        updateConfiguration({ ...configuration, globalDerivations: structuredClone(globalRules) });
        setGlobalDraft(null);
        setGlobalOpen(false);
      }}>
      <div className="canvas-derive-modal-layout">
        <div className="canvas-derive-modal-rule-list">
          <RuleList title="全局规则" scope="GLOBAL" rules={globalRules} selection={globalSelection}
            onSelect={setGlobalSelection}
            onAdd={() => {
              const rules = [...globalRules, defaultRule(globalColumns)];
              setGlobalDraft(rules);
              setGlobalSelection({ scope: 'GLOBAL', index: rules.length - 1 });
            }}
            onMove={(index, offset) => {
              setGlobalDraft(moveRule(globalRules, index, offset));
              setGlobalSelection(movedRuleSelection(globalSelection, 'GLOBAL', index, offset));
            }}
            onRemove={(index) => {
              const rules = globalRules.filter((_, ruleIndex) => ruleIndex !== index);
              setGlobalDraft(rules);
              setGlobalSelection(rules.length ? { scope: 'GLOBAL', index: Math.min(index, rules.length - 1) } : null);
            }} />
        </div>
        <div className="canvas-derive-modal-rule-detail">
          <DerivationRuleEditor derivation={selectedGlobal} columns={globalColumns}
            writeStatus={selectedGlobal
              ? globalDerivationStatus(selectedGlobal, configuration.operations, inputTables)
              : '自动 · 待判断'}
            executionMode={executionMode} eventTimeColumn={null}
            onChange={(rule) => {
              if (globalSelection?.scope !== 'GLOBAL') return;
              const rules = [...globalRules];
              rules[globalSelection.index] = rule;
              setGlobalDraft(rules);
            }} />
        </div>
      </div>
    </Modal>

    <Modal open={Boolean(activeOperation)} width={1040} className="canvas-derive-rules-modal"
      title={activeOperation ? `配置处理表 · ${activeOperation.sourceTableName}` : '配置处理表'} styles={{ body: { overflow: 'hidden' } }} destroyOnHidden
      onCancel={() => setTableDraft(null)} okText="保存此项" onOk={() => {
        if (tableDraft) {
          updateConfiguration({ ...configuration, operations: configuration.operations.map((operation) => (
            operation.operationId === tableDraft.operationId ? structuredClone(tableDraft) : operation
          )) });
        }
        setTableDraft(null);
      }}>
      {activeOperation && <>
        <div className="canvas-derive-table-output-settings">
          <Typography.Text type="secondary">来源表</Typography.Text><Typography.Text code>{activeOperation.sourceTableName}</Typography.Text>
          <Radio.Group size="small" optionType="button" buttonStyle="solid" value={activeOperation.output.mode}
            options={[{ value: 'REPLACE_SOURCE', label: '更新当前表' }, { value: 'CREATE_NEW_TABLE', label: '生成新表' }]}
            onChange={(event) => updateActiveOperation({ ...activeOperation, output: event.target.value === 'CREATE_NEW_TABLE'
              ? { mode: 'CREATE_NEW_TABLE', outputTableName: activeOperation.output.outputTableName ?? '' }
              : { mode: 'REPLACE_SOURCE', outputTableName: null } })} />
          {activeOperation.output.mode === 'CREATE_NEW_TABLE' && <Input style={{ width: 240 }} value={activeOperation.output.outputTableName ?? ''} placeholder="新逻辑表名" onChange={(event) => updateActiveOperation({ ...activeOperation, output: { mode: 'CREATE_NEW_TABLE', outputTableName: event.target.value } })} />}
        </div>
        <div className="canvas-derive-modal-layout">
          <div className="canvas-derive-modal-rule-list">
            <RuleList title="全局规则" scope="GLOBAL" rules={configuration.globalDerivations} selection={tableSelection} readOnly onSelect={setTableSelection} />
            <RuleList title="本表规则" scope="LOCAL" rules={activeOperation.derivations} selection={tableSelection} onSelect={setTableSelection}
              onAdd={() => {
                const rules = [...activeOperation.derivations, defaultRule(activeTable?.columns ?? [])];
                updateActiveOperation({ ...activeOperation, derivations: rules });
                setTableSelection({ scope: 'LOCAL', index: rules.length - 1 });
              }}
              onMove={(index, offset) => {
                updateActiveOperation({ ...activeOperation, derivations: moveRule(activeOperation.derivations, index, offset) });
                setTableSelection(movedRuleSelection(tableSelection, 'LOCAL', index, offset));
              }}
              onRemove={(index) => {
                const rules = activeOperation.derivations.filter((_, ruleIndex) => ruleIndex !== index);
                updateActiveOperation({ ...activeOperation, derivations: rules });
                setTableSelection(rules.length ? { scope: 'LOCAL', index: Math.min(index, rules.length - 1) } : configuration.globalDerivations.length ? { scope: 'GLOBAL', index: 0 } : null);
              }} />
          </div>
          <div className="canvas-derive-modal-rule-detail">
            {tableSelection?.scope === 'GLOBAL'
              ? <DerivationRuleEditor derivation={configuration.globalDerivations[tableSelection.index] ?? null} columns={globalColumns}
                writeStatus={automaticDerivationStatus(
                  configuration.globalDerivations[tableSelection.index] ?? {
                    targetColumnName: '', expression: columnExpression([]),
                  },
                  activeTable?.columns,
                )}
                executionMode={executionMode} eventTimeColumn={activeTable?.eventTimeColumn ?? null} readOnly onChange={() => undefined} onEditGlobal={() => {
                if (tableDraft) {
                  updateConfiguration({ ...configuration, operations: configuration.operations.map((operation) => (
                    operation.operationId === tableDraft.operationId ? structuredClone(tableDraft) : operation
                  )) });
                }
                setTableDraft(null);
                setGlobalDraft(structuredClone(configuration.globalDerivations));
                setGlobalSelection({ scope: 'GLOBAL', index: tableSelection.index });
                setGlobalOpen(true);
              }} />
              : <DerivationRuleEditor derivation={selectedLocal} columns={activeTable?.columns ?? []}
                writeStatus={selectedLocal
                  ? automaticDerivationStatus(selectedLocal, activeTable?.columns)
                  : '自动 · 待判断'}
                executionMode={executionMode} eventTimeColumn={activeTable?.eventTimeColumn ?? null}
                onChange={(rule) => {
                  if (tableSelection?.scope !== 'LOCAL') return;
                  const rules = [...activeOperation.derivations];
                  rules[tableSelection.index] = rule;
                  updateActiveOperation({ ...activeOperation, derivations: rules });
                }} />}
          </div>
        </div>
      </>}
    </Modal>

    {managerOpen && <ProcessorTablePickerModal
      open={managerOpen}
      type={CanvasNodeType.DeriveColumns}
      inputTables={inputTables}
      operations={configuration.operations as unknown as ProcessorOperationDraft[]}
      onCancel={() => setManagerOpen(false)}
      onConfirm={(operations) => {
        updateConfiguration({
          ...configuration,
          operations: operations as unknown as DeriveColumnsOperation[],
        });
        setManagerOpen(false);
      }}
    />}
  </Space>;
};
