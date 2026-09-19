import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Radio, Select, Space, Typography } from 'antd';
import {
  useImperativeHandle,
  useMemo,
  useState,
  type Ref,
} from 'react';
import {
  CANVAS_TOP_N_MAX_LIMIT,
  CanvasNodeType,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasNodeValidationResult,
  type TopNConfiguration,
  type TopNTieStrategy,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../CanvasNodeInspector';
import { OrderedColumnListEditor } from './OrderedColumnListEditor';
import { ProcessorValidationIssues } from './ProcessorValidationIssues';
import { SortFieldsEditor } from './SortFieldsEditor';

interface TopNProcessorInspectorProps {
  node: Extract<CanvasNodeDefinition, { type: 'TOP_N' }>;
  executionMode: 'BATCH' | 'STREAMING';
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

interface TopNFormValues {
  sourceTableName: string;
  outputTableName: string;
}

type TopNScope = 'GLOBAL' | 'PARTITIONED';

export const TopNProcessorInspector = ({
  node,
  executionMode,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: TopNProcessorInspectorProps) => {
  const [form] = Form.useForm<TopNFormValues>();
  const [scope, setScope] = useState<TopNScope>(
    node.configuration.partitionByColumns.length === 0 ? 'GLOBAL' : 'PARTITIONED',
  );
  const [partitionByColumns, setPartitionByColumns] = useState<string[]>(
    () => [...node.configuration.partitionByColumns],
  );
  const [orderBy, setOrderBy] = useState(
    () => structuredClone(node.configuration.orderBy),
  );
  const [limit, setLimit] = useState(node.configuration.limit);
  const [tieStrategy, setTieStrategy] = useState<TopNTieStrategy>(
    node.configuration.tieStrategy,
  );
  const [draftError, setDraftError] = useState<string | null>(null);
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const inputTables = useMemo(() => validation?.inputTables ?? [], [validation?.inputTables]);
  const sourceTable = inputTables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const columns = useMemo(() => sourceTable?.columns ?? [], [sourceTable?.columns]);
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

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      if (executionMode !== 'BATCH') {
        setDraftError('TOP_N 仅支持批处理任务');

      }
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        const effectivePartitions = scope === 'GLOBAL' ? [] : partitionByColumns;
        if (scope === 'PARTITIONED' && effectivePartitions.length === 0) {
          setDraftError('每组前 N 至少需要一个分组字段');

        }
        if (new Set(effectivePartitions).size !== effectivePartitions.length) {
          setDraftError('分组字段不能重复');

        }
        if (orderBy.length === 0) {
          setDraftError('至少配置一个排序字段');

        }
        const sortNames = orderBy.map((field) => field.columnName);
        if (sortNames.some((name) => !name)
          || new Set(sortNames).size !== sortNames.length) {
          setDraftError('排序字段不能为空或重复');

        }
        if (!Number.isInteger(limit) || limit < 1 || limit > CANVAS_TOP_N_MAX_LIMIT) {
          setDraftError(`N 必须是 1..${CANVAS_TOP_N_MAX_LIMIT} 的整数`);

        }
        const configuration: TopNConfiguration = {
          sourceTableName: values.sourceTableName ?? '',
          outputTableName: (values.outputTableName ?? '').trim(),
          partitionByColumns: [...effectivePartitions],
          orderBy: structuredClone(orderBy),
          limit,
          tieStrategy,
        };
        onApply({
          id: node.id,
          type: CanvasNodeType.TopN,
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
    limit,
    node.id,
    onApply,
    onDirtyChange,
    orderBy,
    partitionByColumns,
    scope,
    tieStrategy,
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
          title="Top N 仅支持批处理"
          description="持续流式 Top N 需要状态 TTL 和结果撤回语义，应使用独立流式节点。"
        />
      )}
      <Form<TopNFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，Top N 配置仍被保留。' : undefined}
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
          <Input placeholder="例如 top_orders_by_customer" />
        </Form.Item>
      </Form>

      <section className="canvas-processor-editor-section">
        <Typography.Text strong>选择范围</Typography.Text>
        <Radio.Group
          value={scope}
          className="canvas-top-n-scope"
          onChange={(event) => {
            const next = event.target.value as TopNScope;
            setScope(next);
            if (next === 'GLOBAL') setPartitionByColumns([]);
            dirty();
          }}
        >
          <Radio value="GLOBAL">
            <span><strong>全局前 N</strong> · 整张表共同排序</span>
          </Radio>
          <Radio value="PARTITIONED">
            <span><strong>每组前 N</strong> · 每个分组独立排序</span>
          </Radio>
        </Radio.Group>
      </section>
      {scope === 'PARTITIONED' && (
        <OrderedColumnListEditor
          label="分组字段"
          items={partitionByColumns}
          columns={columns}
          addLabel="添加分组字段"
          emptyText="至少添加一个分组字段。"
          onChange={(next) => {
            setPartitionByColumns(next);
            dirty();
          }}
        />
      )}
      <SortFieldsEditor
        fields={orderBy}
        columns={columns}
        onChange={(next) => {
          setOrderBy(next);
          dirty();
        }}
      />
      <section className="canvas-processor-editor-section">
        <Typography.Text strong>N 与并列策略</Typography.Text>
        <InputNumber
          min={1}
          max={CANVAS_TOP_N_MAX_LIMIT}
          precision={0}
          value={limit}
          addonBefore="前"
          addonAfter="行"
          onChange={(next) => {
            setLimit(next ?? 1);
            dirty();
          }}
        />
        <Radio.Group
          value={tieStrategy}
          className="canvas-top-n-tie-strategy"
          onChange={(event) => {
            setTieStrategy(event.target.value as TopNTieStrategy);
            dirty();
          }}
        >
          <Radio value="EXACT">
            <span>
              <strong>精确 N 行</strong>
              {' · 并列时最多保留 N 行；稳定结果需要唯一排序字段'}
            </span>
          </Radio>
          <Radio value="WITH_TIES">
            <span>
              <strong>保留并列</strong>
              {' · 第 N 行的全部并列记录都保留，结果可能超过 N'}
            </span>
          </Radio>
        </Radio.Group>
      </section>
      {draftError && <Alert showIcon type="error" title={draftError} />}
      <Alert
        showIcon
        type="info"
        title="排序用于选行，不保证写出顺序"
        description="后续 Processor 或 Sink 不能依赖当前排序；消费端查询仍需显式 ORDER BY。"
      />
    </Space>
  );
};
