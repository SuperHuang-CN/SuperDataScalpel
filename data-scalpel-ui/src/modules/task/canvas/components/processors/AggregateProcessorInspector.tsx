import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Form,
  Input,
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
  CanvasNodeType,
  type AggregateConfiguration,
  type AggregateFunction,
  type AggregateItem,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';

interface AggregateProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'AGGREGATE' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface AggregateFormValues {
  sourceTableName: string;
  outputTableName: string;
}

const aggregateFunctionOptions: { value: AggregateFunction; label: string }[] = [
  { value: 'COUNT', label: 'COUNT · 计数' },
  { value: 'SUM', label: 'SUM · 求和' },
  { value: 'AVG', label: 'AVG · 平均值' },
  { value: 'MIN', label: 'MIN · 最小值' },
  { value: 'MAX', label: 'MAX · 最大值' },
];

const COUNT_STAR = '__DATASCALPEL_COUNT_STAR__';

const move = <T,>(items: T[], from: number, to: number): T[] => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

const nextOutputName = (items: AggregateItem[]): string => {
  const names = new Set(items.map((item) => item.outputColumnName));
  let index = 1;
  while (names.has(`count_${index}`)) index += 1;
  return `count_${index}`;
};

export const AggregateProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: AggregateProcessorInspectorProps) => {
  const [form] = Form.useForm<AggregateFormValues>();
  const [groupByColumns, setGroupByColumns] = useState<string[]>(
    () => [...node.configuration.groupByColumns],
  );
  const [aggregations, setAggregations] = useState<AggregateItem[]>(
    () => structuredClone(node.configuration.aggregations),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const sourceColumns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const sourceColumnNames = useMemo(
    () => new Set(sourceColumns.map((column) => column.name)),
    [sourceColumns],
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
  const availableGroupByOptions = sourceColumns
    .filter((column) => !groupByColumns.includes(column.name))
    .map((column) => ({
      value: column.name,
      label: `${column.name} · ${column.fieldType}`,
    }));

  const updateGroupByColumns = (next: string[]) => {
    setGroupByColumns(next);
    setDraftError(null);
    onDirtyChange(true);
  };

  const updateAggregations = (next: AggregateItem[]) => {
    setAggregations(next);
    setDraftError(next.length === 0 ? '至少配置一个聚合项' : null);
    onDirtyChange(true);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      if (executionMode !== 'BATCH') {
        setDraftError('AGGREGATE 仅支持批处理任务');
        return false;
      }
      try {
        const values = await form.validateFields();
        if (aggregations.length === 0) {
          setDraftError('至少配置一个聚合项');
          return false;
        }
        const groupNames = new Set<string>();
        for (const columnName of groupByColumns) {
          if (!columnName) {
            setDraftError('分组字段不能为空');
            return false;
          }
          if (!groupNames.add(columnName)) {
            setDraftError(`分组字段重复：${columnName}`);
            return false;
          }
        }
        const outputNames = new Set<string>();
        for (const item of aggregations) {
          if (!item.function) {
            setDraftError('聚合项中存在未选择函数');
            return false;
          }
          if (!item.outputColumnName.trim()) {
            setDraftError('聚合输出字段名不能为空');
            return false;
          }
          if (!outputNames.add(item.outputColumnName.trim())) {
            setDraftError(`聚合输出字段名重复：${item.outputColumnName.trim()}`);
            return false;
          }
          if (groupNames.has(item.outputColumnName.trim())) {
            setDraftError(`聚合输出字段与分组字段同名：${item.outputColumnName.trim()}`);
            return false;
          }
          if (item.sourceColumnName === null) {
            if (item.function !== 'COUNT' || item.distinct) {
              setDraftError('只有非 DISTINCT 的 COUNT 才能使用全部行（*）');
              return false;
            }
          } else if (!item.sourceColumnName) {
            setDraftError(`${item.function} 必须选择来源字段`);
            return false;
          }
          if (item.distinct && (item.function === 'MIN' || item.function === 'MAX')) {
            setDraftError(`${item.function} 不支持 DISTINCT`);
            return false;
          }
        }
        const configuration: AggregateConfiguration = {
          sourceTableName: values.sourceTableName,
          outputTableName: values.outputTableName.trim(),
          groupByColumns: [...groupByColumns],
          aggregations: aggregations.map((item) => ({
            ...item,
            outputColumnName: item.outputColumnName.trim(),
          })),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.Aggregate,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    aggregations,
    executionMode,
    form,
    groupByColumns,
    node.id,
    onApply,
    onDirtyChange,
  ]);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ProcessorValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      {executionMode !== 'BATCH' && (
        <Alert
          showIcon
          type="error"
          title="聚合节点仅支持批处理"
          description="实时聚合需要窗口、事件时间和 Watermark，请使用后续的窗口聚合节点。"
        />
      )}
      <Form<AggregateFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，聚合配置已保留。' : undefined}
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
          <Input placeholder="例如 customer_order_metrics" />
        </Form.Item>
      </Form>

      <section className="canvas-aggregate-section">
        <div className="canvas-aggregate-heading">
          <div>
            <Typography.Text strong>分组字段</Typography.Text>
            <Typography.Text type="secondary"> · 不选择表示全表聚合</Typography.Text>
          </div>
          <Tag>{groupByColumns.length}</Tag>
        </div>
        <Select
          showSearch
          optionFilterProp="label"
          value={undefined}
          placeholder={sourceTable ? '添加分组字段' : '先选择来源表'}
          disabled={!sourceTable}
          options={availableGroupByOptions}
          onChange={(columnName: string) => updateGroupByColumns([
            ...groupByColumns,
            columnName,
          ])}
        />
        <div className="canvas-aggregate-group-list">
          {groupByColumns.map((columnName, index) => {
            const missing = Boolean(sourceTable && !sourceColumnNames.has(columnName));
            return (
              <div
                className={`canvas-aggregate-group-item${missing ? ' is-invalid' : ''}`}
                key={`${columnName}-${index}`}
              >
                <div className="canvas-aggregate-group-name">
                  <Tag color="blue">{index + 1}</Tag>
                  <Typography.Text ellipsis title={columnName}>{columnName}</Typography.Text>
                  {missing && <Tag color="error">已失效</Tag>}
                </div>
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<UpOutlined />}
                    aria-label={`上移分组字段 ${columnName}`}
                    disabled={index === 0}
                    onClick={() => updateGroupByColumns(move(groupByColumns, index, index - 1))}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<DownOutlined />}
                    aria-label={`下移分组字段 ${columnName}`}
                    disabled={index === groupByColumns.length - 1}
                    onClick={() => updateGroupByColumns(move(groupByColumns, index, index + 1))}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`删除分组字段 ${columnName}`}
                    onClick={() => updateGroupByColumns(
                      groupByColumns.filter((_, itemIndex) => itemIndex !== index),
                    )}
                  />
                </Space>
              </div>
            );
          })}
        </div>
      </section>

      <section className="canvas-aggregate-section">
        <div className="canvas-aggregate-heading">
          <Typography.Text strong>聚合指标</Typography.Text>
          <Button
            size="small"
            icon={<PlusOutlined />}
            onClick={() => updateAggregations([...aggregations, {
              function: 'COUNT',
              sourceColumnName: null,
              outputColumnName: nextOutputName(aggregations),
              distinct: false,
            }])}
          >
            添加
          </Button>
        </div>
        {draftError && <Alert showIcon type="error" title={draftError} />}
        <div className="canvas-aggregate-list">
          {aggregations.length === 0 && (
            <Typography.Text type="secondary">尚未配置聚合指标。</Typography.Text>
          )}
          {aggregations.map((item, index) => {
            const missingSource = Boolean(
              item.sourceColumnName
              && sourceTable
              && !sourceColumnNames.has(item.sourceColumnName),
            );
            const distinctInvalid = item.distinct
              && (item.function === 'MIN' || item.function === 'MAX');
            const countStarInvalid = item.sourceColumnName === null
              && (item.function !== 'COUNT' || item.distinct);
            const invalid = missingSource || distinctInvalid || countStarInvalid;
            const sourceOptions = [
              ...(item.function === 'COUNT'
                ? [{ value: COUNT_STAR, label: '全部行（*）' }]
                : []),
              ...(missingSource ? [{
                value: item.sourceColumnName as string,
                label: `${item.sourceColumnName}（已失效）`,
                disabled: true,
              }] : []),
              ...sourceColumns.map((column) => ({
                value: column.name,
                label: `${column.name} · ${column.fieldType}`,
              })),
            ];
            return (
              <div
                className={`canvas-aggregate-item${invalid ? ' is-invalid' : ''}`}
                key={index}
              >
                <div className="canvas-aggregate-item-heading">
                  <Tag color="purple">指标 {index + 1}</Tag>
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      icon={<UpOutlined />}
                      aria-label={`上移聚合指标 ${index + 1}`}
                      disabled={index === 0}
                      onClick={() => updateAggregations(move(aggregations, index, index - 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<DownOutlined />}
                      aria-label={`下移聚合指标 ${index + 1}`}
                      disabled={index === aggregations.length - 1}
                      onClick={() => updateAggregations(move(aggregations, index, index + 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除聚合指标 ${index + 1}`}
                      onClick={() => updateAggregations(
                        aggregations.filter((_, itemIndex) => itemIndex !== index),
                      )}
                    />
                  </Space>
                </div>
                <div className="canvas-aggregate-function-row">
                  <Select
                    value={item.function}
                    placeholder="聚合函数"
                    options={aggregateFunctionOptions}
                    onChange={(aggregateFunction: AggregateFunction) => {
                      const next = [...aggregations];
                      next[index] = { ...item, function: aggregateFunction };
                      updateAggregations(next);
                    }}
                  />
                  <Select
                    showSearch
                    optionFilterProp="label"
                    value={item.sourceColumnName === null
                      ? COUNT_STAR
                      : item.sourceColumnName || undefined}
                    status={missingSource || countStarInvalid ? 'error' : undefined}
                    placeholder="来源字段"
                    options={sourceOptions}
                    onChange={(value: string) => {
                      const next = [...aggregations];
                      next[index] = {
                        ...item,
                        sourceColumnName: value === COUNT_STAR ? null : value,
                      };
                      updateAggregations(next);
                    }}
                  />
                </div>
                <div className="canvas-aggregate-output-row">
                  <Input
                    value={item.outputColumnName}
                    status={!item.outputColumnName.trim() ? 'error' : undefined}
                    placeholder="输出字段名"
                    onChange={(event) => {
                      const next = [...aggregations];
                      next[index] = { ...item, outputColumnName: event.target.value };
                      updateAggregations(next);
                    }}
                  />
                  <Space size={6} className="canvas-aggregate-distinct">
                    <Switch
                      size="small"
                      checked={item.distinct}
                      onChange={(distinct) => {
                        const next = [...aggregations];
                        next[index] = { ...item, distinct };
                        updateAggregations(next);
                      }}
                    />
                    <Typography.Text type={distinctInvalid || countStarInvalid ? 'danger' : undefined}>
                      DISTINCT
                    </Typography.Text>
                  </Space>
                </div>
                {missingSource && (
                  <Typography.Text type="danger">
                    来源字段已失效，原配置已保留。
                  </Typography.Text>
                )}
                {distinctInvalid && (
                  <Typography.Text type="danger">
                    {item.function} 不支持 DISTINCT。
                  </Typography.Text>
                )}
                {countStarInvalid && (
                  <Typography.Text type="danger">
                    COUNT(*) 不能与 DISTINCT 或其他聚合函数组合。
                  </Typography.Text>
                )}
              </div>
            );
          })}
        </div>
      </section>
    </Space>
  );
};
