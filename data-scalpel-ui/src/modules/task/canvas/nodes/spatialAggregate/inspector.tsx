import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialAggregateConfiguration,
  type SpatialAggregation,
  type SpatialAggregationKind,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import {
  spatialColumnOptions,
  spatialGeometryColumns,
  spatialTableOptions,
} from '../spatialInspectorOptions';

interface SpatialAggregateFormValues {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
}

const aggregationKindOptions: Array<{ value: SpatialAggregationKind; label: string }> = [
  { value: 'UNION', label: 'UNION · 拓扑融合' },
  { value: 'INTERSECTION', label: 'INTERSECTION · 公共部分' },
  { value: 'COLLECT', label: 'COLLECT · 集合收集' },
  { value: 'ENVELOPE', label: 'ENVELOPE · 总包络' },
];

const defaultOutputName = (kind: SpatialAggregationKind) => (
  `${kind.toLowerCase()}_geometry`
);

const createAggregation = (
  columns: CanvasColumnSchema[],
  kind: SpatialAggregationKind = 'UNION',
): SpatialAggregation => ({
  kind,
  geometryColumnName: columns[0]?.name ?? '',
  outputColumnName: defaultOutputName(kind),
});

const fingerprint = (value: SpatialAggregateConfiguration) => JSON.stringify(value);

const SpatialAggregateInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialAggregate>) => {
  const [form] = Form.useForm<SpatialAggregateFormValues>();
  const [aggregations, setAggregations] = useState<SpatialAggregation[]>(
    node.configuration.aggregations,
  );
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const groupByColumns = Form.useWatch('groupByColumns', form)
    ?? node.configuration.groupByColumns;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const scalarColumns = sourceTable?.columns.filter(
    (column) => column.fieldType !== 'GEOMETRY',
  ) ?? [];
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const invalidGroupColumns = groupByColumns.filter(
    (name) => !scalarColumns.some((column) => column.name === name),
  );

  const configuration = (
    values: SpatialAggregateFormValues,
    items: SpatialAggregation[],
  ): SpatialAggregateConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    groupByColumns: values.groupByColumns ?? [],
    aggregations: items.map((item) => ({
      ...item,
      outputColumnName: item.outputColumnName.trim(),
    })),
  });
  const markDirty = (items = aggregations) => {
    onDirtyChange(
      fingerprint(configuration(form.getFieldsValue(), items))
        !== fingerprint(node.configuration),
    );
  };
  const updateAggregations = (items: SpatialAggregation[]) => {
    setAggregations(items);
    markDirty(items);
  };
  const updateAggregation = (index: number, item: SpatialAggregation) => {
    updateAggregations(aggregations.map((candidate, itemIndex) => (
      itemIndex === index ? item : candidate
    )));
  };
  const move = (from: number, to: number) => {
    const next = [...aggregations];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    updateAggregations(next);
  };
  const submit = (values: SpatialAggregateFormValues) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: configuration(values, aggregations),
    });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        try {
          void form.validateFields().catch(() => undefined);
          submit(form.getFieldsValue(true));
          return true;
        } catch {
          return false;
        }
      },
    }),
  );

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Alert
        showIcon
        type="info"
        title="有界批处理空间聚合"
        description="NULL 被忽略；Empty 按 UNION、INTERSECTION、COLLECT、ENVELOPE 各自的空间语义处理。每个结果独立继承来源字段 CRS。"
      />
      <Form<SpatialAggregateFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
          groupByColumns: node.configuration.groupByColumns,
        }}
        onFinish={submit}
        onValuesChange={() => markDirty()}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，全部聚合配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)}
            placeholder={validation ? '选择有界来源表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item
          name="groupByColumns"
          label="分组字段"
          validateStatus={invalidGroupColumns.length > 0 ? 'error' : undefined}
          help={invalidGroupColumns.length > 0
            ? `以下字段已失效或是 Geometry：${invalidGroupColumns.join('、')}`
            : groupByColumns.length === 0 ? '未选择时执行全局空间聚合。' : undefined}
        >
          <Select
            mode="multiple"
            showSearch
            optionFilterProp="label"
            disabled={!sourceTable}
            maxTagCount="responsive"
            options={[
              ...invalidGroupColumns.map((name) => ({
                value: name,
                label: `${name}（已失效）`,
                disabled: true,
              })),
              ...scalarColumns.map((column) => ({
                value: column.name,
                label: `${column.name} · ${column.fieldType}`,
              })),
            ]}
            placeholder="留空表示全局聚合"
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 district_geometry" />
        </Form.Item>
      </Form>

      <div className="canvas-processor-section-header">
        <span>
          <Typography.Text strong>空间聚合项</Typography.Text>
          <Typography.Text type="secondary">
            {` · ${aggregations.length}/${CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS}`}
          </Typography.Text>
        </span>
        <Button
          size="small"
          icon={<PlusOutlined />}
          disabled={aggregations.length >= CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS}
          onClick={() => updateAggregations([
            ...aggregations,
            createAggregation(geometryColumns),
          ])}
        >
          添加聚合
        </Button>
      </div>
      {aggregations.length === 0 && (
        <Typography.Text type="secondary">
          至少添加一个 UNION、INTERSECTION、COLLECT 或 ENVELOPE。
        </Typography.Text>
      )}
      <div className="canvas-processor-rule-list">
        {aggregations.map((aggregation, index) => {
          const sourceColumn = geometryColumns.find(
            (column) => column.name === aggregation.geometryColumnName,
          );
          const sourceMissing = Boolean(aggregation.geometryColumnName && !sourceColumn);
          const duplicateOutput = aggregations.some(
            (candidate, candidateIndex) => candidateIndex !== index
              && candidate.outputColumnName === aggregation.outputColumnName,
          );
          const groupConflict = groupByColumns.includes(aggregation.outputColumnName);
          return (
            <Card
              size="small"
              key={index}
              className={`canvas-processor-rule-card${
                sourceMissing || duplicateOutput || groupConflict ? ' is-invalid' : ''
              }`}
              title={(
                <Space size={6} wrap>
                  <Tag color="purple">{index + 1}</Tag>
                  <span>{aggregation.kind}</span>
                  {sourceMissing && <Tag color="error">字段已失效</Tag>}
                  {(duplicateOutput || groupConflict) && <Tag color="error">输出名冲突</Tag>}
                </Space>
              )}
              extra={(
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<UpOutlined />}
                    aria-label={`上移空间聚合 ${index + 1}`}
                    disabled={index === 0}
                    onClick={() => move(index, index - 1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<DownOutlined />}
                    aria-label={`下移空间聚合 ${index + 1}`}
                    disabled={index === aggregations.length - 1}
                    onClick={() => move(index, index + 1)}
                  />
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    aria-label={`删除空间聚合 ${index + 1}`}
                    onClick={() => updateAggregations(
                      aggregations.filter((_, itemIndex) => itemIndex !== index),
                    )}
                  />
                </Space>
              )}
            >
              <Space orientation="vertical" size={8} className="canvas-full-width">
                <Select
                  value={aggregation.kind}
                  options={aggregationKindOptions}
                  onChange={(kind: SpatialAggregationKind) => updateAggregation(
                    index,
                    { ...aggregation, kind },
                  )}
                />
                <Select
                  showSearch
                  optionFilterProp="label"
                  value={aggregation.geometryColumnName || undefined}
                  status={sourceMissing ? 'error' : undefined}
                  options={spatialColumnOptions(
                    sourceTable?.columns ?? [],
                    aggregation.geometryColumnName,
                    (column) => column.fieldType === 'GEOMETRY',
                  )}
                  placeholder="Geometry 来源字段"
                  onChange={(geometryColumnName) => updateAggregation(
                    index,
                    { ...aggregation, geometryColumnName },
                  )}
                />
                {sourceColumn?.geometry && (
                  <Tag color="purple">
                    {`${sourceColumn.geometry.kind} · ${sourceColumn.geometry.crs.authority}:${sourceColumn.geometry.crs.code} · ${sourceColumn.geometry.dimension}`}
                  </Tag>
                )}
                <Input
                  value={aggregation.outputColumnName}
                  status={duplicateOutput || groupConflict ? 'error' : undefined}
                  placeholder={defaultOutputName(aggregation.kind)}
                  onChange={(event) => updateAggregation(
                    index,
                    { ...aggregation, outputColumnName: event.target.value },
                  )}
                />
              </Space>
            </Card>
          );
        })}
      </div>
      <Typography.Text type="secondary">
        输出顺序固定为分组字段在前、空间聚合字段在后；聚合结果统一声明为通用 GEOMETRY。
      </Typography.Text>
    </Space>
  );
};

export default SpatialAggregateInspector;
