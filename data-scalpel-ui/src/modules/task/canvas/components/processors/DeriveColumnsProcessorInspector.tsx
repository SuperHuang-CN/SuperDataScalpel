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
  Radio,
  Select,
  Space,
  Switch,
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
  CANVAS_EXPRESSION_MAX_CASE_BRANCHES,
  CANVAS_EXPRESSION_MAX_DEPTH,
  CANVAS_EXPRESSION_MAX_DERIVATIONS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasExpression,
  type CanvasLiteral,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type ColumnDerivation,
  type DeriveBinaryOperator,
  type DeriveColumnsConfiguration,
  type DeriveFunction,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { FilterConditionTreeEditor } from './FilterProcessorInspector';
import {
  createDefaultFilterCondition,
  validateFilterConditionDraft,
} from './filterConditionDraft';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface DeriveColumnsProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'DERIVE_COLUMNS' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface DeriveColumnsFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const expressionKindOptions: Array<{ value: CanvasExpression['kind']; label: string }> = [
  { value: 'COLUMN', label: '字段引用' },
  { value: 'LITERAL', label: '固定值' },
  { value: 'BINARY', label: '二元运算' },
  { value: 'FUNCTION', label: '函数' },
  { value: 'CASE_WHEN', label: '条件 CASE' },
];

const binaryOperatorOptions: Array<{ value: DeriveBinaryOperator; label: string }> = [
  { value: 'ADD', label: '加 +' },
  { value: 'SUBTRACT', label: '减 −' },
  { value: 'MULTIPLY', label: '乘 ×' },
  { value: 'DIVIDE', label: '除 ÷' },
  { value: 'MODULO', label: '取模 %' },
];

const functionOptions: Array<{ value: DeriveFunction; label: string }> = [
  'TRIM',
  'LTRIM',
  'RTRIM',
  'LOWER',
  'UPPER',
  'REPLACE',
  'SUBSTRING',
  'COALESCE',
  'CONCAT',
  'DATE_FORMAT',
  'DATE_ADD',
  'DATE_SUB',
].map((value) => ({ value: value as DeriveFunction, label: value }));

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
      return expression.columnName ? `字段 ${expression.columnName}` : '未选择字段';
    case 'LITERAL':
      return `${expression.literal.dataType} Literal`;
    case 'BINARY':
      return `${expression.operator} 运算`;
    case 'FUNCTION':
      return `${expression.function}(${expression.arguments.length})`;
    case 'CASE_WHEN':
      return `CASE · ${expression.branches.length} 个分支`;
  }
};

const expressionNodeCount = (expression: CanvasExpression): number => {
  switch (expression.kind) {
    case 'COLUMN':
    case 'LITERAL':
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
  onChange: (expression: CanvasExpression) => void;
}

const ExpressionEditor = ({
  expression,
  columns,
  depth,
  onChange,
}: ExpressionEditorProps) => {
  const nestedDisabled = depth >= CANVAS_EXPRESSION_MAX_DEPTH;
  const kindOptions = expressionKindOptions.map((option) => ({
    ...option,
    disabled: nestedDisabled
      && option.value !== 'COLUMN'
      && option.value !== 'LITERAL',
  }));
  const editorHeader = (
    <Select
      size="small"
      value={expression.kind}
      options={kindOptions}
      onChange={(kind) => onChange(defaultExpression(kind, columns))}
    />
  );

  if (expression.kind === 'COLUMN') {
    const missing = Boolean(
      expression.columnName
      && !columns.some((column) => column.name === expression.columnName),
    );
    return (
      <Card size="small" className="canvas-expression-card" title={editorHeader}>
        <Select
          showSearch
          optionFilterProp="label"
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
        {missing && (
          <Typography.Text type="danger">原字段已失效，引用值已保留。</Typography.Text>
        )}
      </Card>
    );
  }

  if (expression.kind === 'LITERAL') {
    return (
      <Card size="small" className="canvas-expression-card" title={editorHeader}>
        <Space.Compact block>
          <Select
            className="canvas-expression-literal-type"
            value={expression.literal.dataType}
            options={literalTypeOptions}
            onChange={(dataType) => onChange({
              ...expression,
              literal: { dataType, value: expression.literal.value ?? '' },
            })}
          />
          <Input
            autoComplete="off"
            value={expression.literal.value ?? ''}
            placeholder="稳定字符串值"
            onChange={(event) => onChange({
              ...expression,
              literal: { ...expression.literal, value: event.target.value },
            })}
          />
        </Space.Compact>
      </Card>
    );
  }

  if (expression.kind === 'BINARY') {
    return (
      <Card size="small" className="canvas-expression-card" title={editorHeader}>
        <Select
          value={expression.operator}
          options={binaryOperatorOptions}
          onChange={(operator) => onChange({ ...expression, operator })}
        />
        <div className="canvas-expression-children">
          <ExpressionEditor
            expression={expression.left}
            columns={columns}
            depth={depth + 1}
            onChange={(left) => onChange({ ...expression, left })}
          />
          <ExpressionEditor
            expression={expression.right}
            columns={columns}
            depth={depth + 1}
            onChange={(right) => onChange({ ...expression, right })}
          />
        </div>
      </Card>
    );
  }

  if (expression.kind === 'FUNCTION') {
    const variableArguments = expression.function === 'COALESCE'
      || expression.function === 'CONCAT';
    return (
      <Card size="small" className="canvas-expression-card" title={editorHeader}>
        <div className="canvas-expression-function-heading">
          <Select
            value={expression.function}
            options={functionOptions}
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
              参数
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
                onChange={(nextArgument) => {
                  const argumentsCopy = [...expression.arguments];
                  argumentsCopy[index] = nextArgument;
                  onChange({ ...expression, arguments: argumentsCopy });
                }}
              />
              {variableArguments && expression.arguments.length > 2 && (
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => onChange({
                    ...expression,
                    arguments: expression.arguments.filter(
                      (_, argumentIndex) => argumentIndex !== index,
                    ),
                  })}
                >
                  删除参数
                </Button>
              )}
            </div>
          ))}
        </div>
      </Card>
    );
  }

  return (
    <Card size="small" className="canvas-expression-card" title={editorHeader}>
      <Space orientation="vertical" size={8} className="canvas-expression-case">
        {expression.branches.map((branch, index) => (
          <Card
            size="small"
            key={index}
            className="canvas-expression-case-branch"
            title={`WHEN ${index + 1}`}
            extra={(
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
            )}
          >
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
              onChange={(result) => {
                const branches = [...expression.branches];
                branches[index] = { ...branch, result };
                onChange({ ...expression, branches });
              }}
            />
          </Card>
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
            onChange={(elseExpression) => onChange({ ...expression, elseExpression })}
          />
        )}
      </Space>
    </Card>
  );
};

export const DeriveColumnsProcessorInspector = ({
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
        const values = await form.validateFields();
        if (derivations.length === 0) {
          setDraftError('至少配置一个派生字段');
          return false;
        }
        const targets = new Set<string>();
        for (const derivation of derivations) {
          if (!derivation.targetColumnName.trim()) {
            setDraftError('派生字段中存在未填写的目标字段名');
            return false;
          }
          if (!targets.add(derivation.targetColumnName.trim())) {
            setDraftError(`目标字段重复配置：${derivation.targetColumnName.trim()}`);
            return false;
          }
          const expressionIssue = validateExpressionDraft(derivation.expression);
          if (expressionIssue) {
            setDraftError(expressionIssue);
            return false;
          }
        }
        const configuration: DeriveColumnsConfiguration = {
          sourceTableName: values.sourceTableName,
          outputTableName: values.outputTableName.trim(),
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
              replaceExisting: false,
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
                {derivation.replaceExisting ? '覆盖' : '新增'} · {expressionSummary(derivation.expression)}
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
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              value={selectedDerivation.replaceExisting ? 'REPLACE' : 'ADD'}
              options={[
                { value: 'ADD', label: '新增字段' },
                { value: 'REPLACE', label: '覆盖字段' },
              ]}
              onChange={(event) => {
                const nextDerivations = [...derivations];
                nextDerivations[selectedIndex] = {
                  ...selectedDerivation,
                  replaceExisting: event.target.value === 'REPLACE',
                };
                updateDerivations(nextDerivations);
              }}
            />
            {selectedDerivation.replaceExisting
              && sourceTable
              && !sourceColumns.some(
                (column) => column.name === selectedDerivation.targetColumnName,
              ) && (
              <Alert showIcon type="error" title="覆盖模式要求目标字段已存在于原始来源 Schema" />
            )}
            {!selectedDerivation.replaceExisting
              && sourceColumns.some(
                (column) => column.name === selectedDerivation.targetColumnName,
              ) && (
              <Alert showIcon type="error" title="新增模式的目标字段不能与原始字段同名" />
            )}
            {executionMode === 'STREAMING'
              && selectedDerivation.replaceExisting
              && selectedDerivation.targetColumnName === sourceTable?.eventTimeColumn && (
              <Alert showIcon type="error" title="流模式不能覆盖事件时间字段" />
            )}
            <ExpressionEditor
              expression={selectedDerivation.expression}
              columns={sourceColumns}
              depth={1}
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
