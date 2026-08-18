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
  Checkbox,
  Form,
  Input,
  InputNumber,
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
  CANVAS_WINDOW_MAX_FRAME_OFFSET,
  CANVAS_WINDOW_MAX_FUNCTIONS,
  CANVAS_WINDOW_MAX_OFFSET,
  CanvasNodeType,
  type AggregateWindowFunction,
  type CanvasColumnSchema,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type RowsFrameBoundary,
  type RowsWindowFrame,
  type ValueWindowFunction,
  type WindowConfiguration,
  type WindowFunctionItem,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { OrderedColumnListEditor } from './OrderedColumnListEditor';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';
import { SortFieldsEditor } from './SortFieldsEditor';
import { createLiteral } from './canvasLiteral';
import { TypedLiteralInput } from './TypedLiteralInput';

interface WindowProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'WINDOW' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface WindowFormValues {
  sourceTableName: string;
  outputTableName: string;
}

type WindowFunctionKind = WindowFunctionItem['kind'];

const functionOptions: Array<{ value: WindowFunctionKind; label: string }> = [
  { value: 'ROW_NUMBER', label: 'ROW_NUMBER · 连续序号' },
  { value: 'RANK', label: 'RANK · 并列且保留间隔' },
  { value: 'DENSE_RANK', label: 'DENSE_RANK · 并列无间隔' },
  { value: 'LAG', label: 'LAG · 前一行值' },
  { value: 'LEAD', label: 'LEAD · 后一行值' },
  { value: 'COUNT', label: 'COUNT · 窗口计数' },
  { value: 'SUM', label: 'SUM · 窗口求和' },
  { value: 'AVG', label: 'AVG · 窗口平均' },
  { value: 'MIN', label: 'MIN · 窗口最小值' },
  { value: 'MAX', label: 'MAX · 窗口最大值' },
  { value: 'FIRST_VALUE', label: 'FIRST_VALUE · 窗口首值' },
  { value: 'LAST_VALUE', label: 'LAST_VALUE · 窗口末值' },
];

const defaultFrame = (lastValue = false): RowsWindowFrame => ({
  type: 'ROWS',
  start: { kind: 'UNBOUNDED_PRECEDING' },
  end: lastValue ? { kind: 'UNBOUNDED_FOLLOWING' } : { kind: 'CURRENT_ROW' },
});

const defaultFunction = (
  kind: WindowFunctionKind,
  columns: CanvasColumnSchema[],
): WindowFunctionItem => {
  const source = columns[0];
  const outputColumnName = kind.toLowerCase();
  if (kind === 'ROW_NUMBER' || kind === 'RANK' || kind === 'DENSE_RANK') {
    return { kind, outputColumnName };
  }
  if (kind === 'LAG' || kind === 'LEAD') {
    return {
      kind,
      sourceColumnName: source?.name ?? '',
      offset: 1,
      defaultValue: null,
      outputColumnName,
    };
  }
  if (kind === 'FIRST_VALUE' || kind === 'LAST_VALUE') {
    return {
      kind,
      sourceColumnName: source?.name ?? '',
      ignoreNulls: false,
      outputColumnName,
      frame: defaultFrame(kind === 'LAST_VALUE'),
    };
  }
  return {
    kind,
    sourceColumnName: kind === 'COUNT' ? null : source?.name ?? '',
    outputColumnName,
    frame: defaultFrame(),
  };
};

const move = <T,>(items: T[], from: number, to: number) => {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
};

interface FrameBoundaryEditorProps {
  label: string;
  value: RowsFrameBoundary;
  onChange: (boundary: RowsFrameBoundary) => void;
}

const FrameBoundaryEditor = ({
  label,
  value,
  onChange,
}: FrameBoundaryEditorProps) => {
  const boundaryOptions = [
    { value: 'UNBOUNDED_PRECEDING', label: '无限向前' },
    { value: 'PRECEDING', label: '向前 N 行' },
    { value: 'CURRENT_ROW', label: '当前行' },
    { value: 'FOLLOWING', label: '向后 N 行' },
    { value: 'UNBOUNDED_FOLLOWING', label: '无限向后' },
  ];
  return (
    <div className="canvas-window-boundary">
      <Typography.Text type="secondary">{label}</Typography.Text>
      <Select
        value={value.kind}
        options={boundaryOptions}
        onChange={(kind: RowsFrameBoundary['kind']) => {
          if (kind === 'PRECEDING' || kind === 'FOLLOWING') {
            onChange({ kind, offset: 1 });
          } else {
            onChange({ kind });
          }
        }}
      />
      {(value.kind === 'PRECEDING' || value.kind === 'FOLLOWING') && (
        <InputNumber
          min={1}
          max={CANVAS_WINDOW_MAX_FRAME_OFFSET}
          precision={0}
          value={value.offset}
          addonAfter="行"
          onChange={(offset) => onChange({ ...value, offset: offset ?? 1 })}
        />
      )}
    </div>
  );
};

interface FrameEditorProps {
  value: RowsWindowFrame;
  onChange: (frame: RowsWindowFrame) => void;
}

const FrameEditor = ({ value, onChange }: FrameEditorProps) => (
  <div className="canvas-window-frame">
    <div className="canvas-window-frame-title">
      <Typography.Text strong>ROWS Frame</Typography.Text>
      <Tag>显式范围</Tag>
    </div>
    <div className="canvas-window-frame-boundaries">
      <FrameBoundaryEditor
        label="起点"
        value={value.start}
        onChange={(start) => onChange({ ...value, start })}
      />
      <FrameBoundaryEditor
        label="终点"
        value={value.end}
        onChange={(end) => onChange({ ...value, end })}
      />
    </div>
  </div>
);

const fieldOptions = (
  columns: CanvasColumnSchema[],
  current: string | null,
  allowCountStar = false,
) => [
  ...(allowCountStar ? [{ value: '__COUNT_STAR__', label: 'COUNT(*) · 统计所有行' }] : []),
  ...(current && !columns.some((column) => column.name === current)
    ? [{ value: current, label: `${current}（已失效）`, disabled: true }]
    : []),
  ...columns.map((column) => ({
    value: column.name,
    label: `${column.name} · ${column.fieldType}`,
  })),
];

export const WindowProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: WindowProcessorInspectorProps) => {
  const [form] = Form.useForm<WindowFormValues>();
  const [partitionByColumns, setPartitionByColumns] = useState<string[]>(
    () => [...node.configuration.partitionByColumns],
  );
  const [orderBy, setOrderBy] = useState(
    () => structuredClone(node.configuration.orderBy),
  );
  const [functions, setFunctions] = useState<WindowFunctionItem[]>(
    () => structuredClone(node.configuration.functions),
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const columns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
  const sourceNames = useMemo(
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

  const dirty = () => {
    setDraftError(null);
    onDirtyChange(true);
  };
  const updateFunctions = (next: WindowFunctionItem[]) => {
    setFunctions(next);
    dirty();
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      if (executionMode !== 'BATCH') {
        setDraftError('WINDOW 仅支持批处理任务');

      }
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (orderBy.length === 0) {
          setDraftError('至少配置一个窗口排序字段');

        }
        if (functions.length === 0) {
          setDraftError('至少配置一个窗口函数');

        }
        if (functions.length > CANVAS_WINDOW_MAX_FUNCTIONS) {
          setDraftError(`窗口函数不能超过 ${CANVAS_WINDOW_MAX_FUNCTIONS} 项`);

        }
        const outputNames = new Set<string>();
        for (const item of functions) {
          if (!item.outputColumnName.trim()) {
            setDraftError('每个窗口函数都必须设置输出字段名');

          }
          if (!outputNames.add(item.outputColumnName.trim())) {
            setDraftError(`窗口输出字段重复：${item.outputColumnName}`);

          }
          if (sourceNames.has(item.outputColumnName.trim())) {
            setDraftError(`窗口输出字段与来源字段冲突：${item.outputColumnName}`);

          }
          if ((item.kind === 'LAG' || item.kind === 'LEAD')
            && (item.offset < 1 || item.offset > CANVAS_WINDOW_MAX_OFFSET)) {
            setDraftError(`LAG/LEAD offset 必须在 1..${CANVAS_WINDOW_MAX_OFFSET}`);

          }
        }
        const configuration: WindowConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          partitionByColumns: [...partitionByColumns],
          orderBy: structuredClone(orderBy),
          functions: structuredClone(functions).map((item) => ({
            ...item,
            outputColumnName: item.outputColumnName.trim(),
          })),
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.Window,
          configuration,
        });
        onDirtyChange(false);
        return true;
      } catch {
        return false;
      }
    },
  }), [
    executionMode,
    form,
    functions,
    node.id,
    onApply,
    onDirtyChange,
    orderBy,
    partitionByColumns,
    sourceNames,
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
          title="窗口计算仅支持批处理"
          description="流式事件时间窗口需要独立的 Watermark 和状态语义。"
        />
      )}
      <Form<WindowFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
        }}
        onValuesChange={dirty}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，窗口配置仍被保留。' : undefined}
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
          <Input placeholder="例如 orders_with_window_metrics" />
        </Form.Item>
      </Form>

      <OrderedColumnListEditor
        label="分区字段"
        items={partitionByColumns}
        columns={columns}
        addLabel="添加分区字段"
        emptyText="不分组：整张表作为一个窗口分区。"
        onChange={(next) => {
          setPartitionByColumns(next);
          dirty();
        }}
      />
      <SortFieldsEditor
        fields={orderBy}
        columns={columns}
        onChange={(next) => {
          setOrderBy(next);
          dirty();
        }}
      />
      <Alert
        showIcon
        type="info"
        title="排序只用于窗口计算"
        description="该排序不保证后续 Processor 或输出端的物理写出顺序。"
      />

      <section className="canvas-processor-editor-section">
        <div className="canvas-processor-editor-heading">
          <span>
            <Typography.Text strong>窗口函数</Typography.Text>
            <Typography.Text type="secondary">
              {` · ${functions.length}/${CANVAS_WINDOW_MAX_FUNCTIONS}`}
            </Typography.Text>
          </span>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={functions.length >= CANVAS_WINDOW_MAX_FUNCTIONS}
            onClick={() => updateFunctions([
              ...functions,
              defaultFunction('ROW_NUMBER', columns),
            ])}
          >
            添加函数
          </Button>
        </div>
        {functions.length === 0 && (
          <Typography.Text type="secondary">
            添加排名、偏移、聚合或窗口取值函数。
          </Typography.Text>
        )}
        <div className="canvas-processor-rule-list">
          {functions.map((item, index) => {
            const update = (next: WindowFunctionItem) => updateFunctions(
              functions.map((candidate, itemIndex) => itemIndex === index ? next : candidate),
            );
            const sourceColumnName = 'sourceColumnName' in item
              ? item.sourceColumnName : null;
            const missingSource = Boolean(
              sourceColumnName && !sourceNames.has(sourceColumnName),
            );
            return (
              <Card
                size="small"
                className={`canvas-processor-rule-card${missingSource ? ' is-invalid' : ''}`}
                key={index}
                title={(
                  <Space>
                    <Tag color="purple">{index + 1}</Tag>
                    <span>{item.kind}</span>
                    {missingSource && <Tag color="error">来源字段已失效</Tag>}
                  </Space>
                )}
                extra={(
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      icon={<UpOutlined />}
                      aria-label={`上移窗口函数 ${index + 1}`}
                      disabled={index === 0}
                      onClick={() => updateFunctions(move(functions, index, index - 1))}
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<DownOutlined />}
                      aria-label={`下移窗口函数 ${index + 1}`}
                      disabled={index === functions.length - 1}
                      onClick={() => updateFunctions(move(functions, index, index + 1))}
                    />
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      aria-label={`删除窗口函数 ${index + 1}`}
                      onClick={() => updateFunctions(
                        functions.filter((_, itemIndex) => itemIndex !== index),
                      )}
                    />
                  </Space>
                )}
              >
                <Space orientation="vertical" size={10} style={{ width: '100%' }}>
                  <Select
                    value={item.kind}
                    options={functionOptions}
                    onChange={(kind: WindowFunctionKind) => update(
                      defaultFunction(kind, columns),
                    )}
                  />
                  {'sourceColumnName' in item && (
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={item.kind === 'COUNT' && item.sourceColumnName === null
                        ? '__COUNT_STAR__'
                        : item.sourceColumnName || undefined}
                      placeholder="选择来源字段"
                      options={fieldOptions(
                        columns,
                        item.sourceColumnName,
                        item.kind === 'COUNT',
                      )}
                      onChange={(value: string) => update({
                        ...item,
                        sourceColumnName: value === '__COUNT_STAR__' ? null : value,
                      } as WindowFunctionItem)}
                    />
                  )}
                  {(item.kind === 'LAG' || item.kind === 'LEAD') && (
                    <>
                      <InputNumber
                        min={1}
                        max={CANVAS_WINDOW_MAX_OFFSET}
                        precision={0}
                        value={item.offset}
                        addonBefore="偏移"
                        addonAfter="行"
                        onChange={(offset) => update({
                          ...item,
                          offset: offset ?? 1,
                        })}
                      />
                      <Checkbox
                        checked={item.defaultValue !== null}
                        onChange={(event) => {
                          const column = columns.find(
                            (candidate) => candidate.name === item.sourceColumnName,
                          );
                          update({
                            ...item,
                            defaultValue: event.target.checked
                              ? createLiteral(column?.fieldType ?? 'STRING')
                              : null,
                          });
                        }}
                      >
                        越界时使用固定默认值
                      </Checkbox>
                      {item.defaultValue !== null && (
                        <TypedLiteralInput
                          dataType={columns.find(
                            (column) => column.name === item.sourceColumnName,
                          )?.fieldType ?? item.defaultValue.dataType}
                          value={item.defaultValue}
                          onChange={(defaultValue) => update({ ...item, defaultValue })}
                        />
                      )}
                    </>
                  )}
                  {(item.kind === 'FIRST_VALUE' || item.kind === 'LAST_VALUE') && (
                    <div className="canvas-window-ignore-nulls">
                      <Typography.Text>忽略 NULL</Typography.Text>
                      <Switch
                        checked={(item as ValueWindowFunction).ignoreNulls}
                        onChange={(ignoreNulls) => update({ ...item, ignoreNulls })}
                      />
                    </div>
                  )}
                  {'frame' in item && (
                    <FrameEditor
                      value={(item as AggregateWindowFunction | ValueWindowFunction).frame}
                      onChange={(frame) => update({ ...item, frame } as WindowFunctionItem)}
                    />
                  )}
                  <Input
                    value={item.outputColumnName}
                    placeholder="输出字段名"
                    addonBefore="输出"
                    onChange={(event) => update({
                      ...item,
                      outputColumnName: event.target.value,
                    } as WindowFunctionItem)}
                  />
                </Space>
              </Card>
            );
          })}
        </div>
      </section>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      <Typography.Text type="secondary">
        同一节点内的函数只能引用原始来源字段，不能引用前一项新生成的窗口字段。
      </Typography.Text>
    </Space>
  );
};
