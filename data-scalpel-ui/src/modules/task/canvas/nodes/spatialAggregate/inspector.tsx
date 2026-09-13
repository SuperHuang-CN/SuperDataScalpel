import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Switch, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS,
  CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialAggregateConfiguration,
  type SpatialAggregateDissolveGroupingMode,
  type SpatialAggregateStatistic,
  type SpatialAggregateStatisticKind,
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
  dissolveEnabled: boolean;
  dissolveGroupingMode: SpatialAggregateDissolveGroupingMode;
  multipart: boolean;
  countOutputColumnName: string;
}

const aggregationKindOptions: Array<{ value: SpatialAggregationKind; label: string }> = [
  { value: 'UNION', label: 'UNION · 拓扑融合' },
  { value: 'INTERSECTION', label: 'INTERSECTION · 公共部分' },
  { value: 'COLLECT', label: 'COLLECT · 集合收集' },
  { value: 'ENVELOPE', label: 'ENVELOPE · 总包络' },
];

const statisticKindOptions: Array<{
  value: SpatialAggregateStatisticKind;
  label: string;
}> = [
  { value: 'COUNT_FIELD', label: 'COUNT · 非空数' },
  { value: 'SUM', label: 'SUM · 求和' },
  { value: 'MEAN', label: 'MEAN · 平均值' },
  { value: 'MIN', label: 'MIN · 最小值' },
  { value: 'MAX', label: 'MAX · 最大值' },
  { value: 'RANGE', label: 'RANGE · 极差' },
  { value: 'STDDEV', label: 'STDDEV · 样本标准差' },
  { value: 'VARIANCE', label: 'VARIANCE · 样本方差' },
  { value: 'ANY', label: 'ANY · 任一字符串' },
];

const dissolveGroupingOptions: Array<{
  value: SpatialAggregateDissolveGroupingMode;
  label: string;
}> = [
  { value: 'ALL_OR_FIELDS', label: '全部 / 按字段值' },
  { value: 'CONNECTED_COMPONENTS', label: '按相交或接触连通组' },
];

const numericFieldTypes = new Set([
  'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
]);

const statisticAcceptsColumn = (
  kind: SpatialAggregateStatisticKind,
  column: CanvasColumnSchema,
) => {
  if (column.fieldType === 'GEOMETRY') return false;
  if (kind === 'COUNT_FIELD') return true;
  if (kind === 'ANY') return column.fieldType === 'STRING';
  return numericFieldTypes.has(column.fieldType);
};

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

const createStatistic = (
  columns: CanvasColumnSchema[],
): SpatialAggregateStatistic => ({
  statisticId: crypto.randomUUID(),
  kind: 'COUNT_FIELD',
  sourceColumnName: columns[0]?.name ?? '',
  outputColumnName: 'value_count',
});

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
  const [summaryStatistics, setSummaryStatistics] = useState<SpatialAggregateStatistic[]>(
    node.configuration.dissolve?.summaryStatistics ?? [],
  );
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const groupByColumns = Form.useWatch('groupByColumns', form)
    ?? node.configuration.groupByColumns;
  const dissolveEnabled = Form.useWatch('dissolveEnabled', form)
    ?? node.configuration.dissolve?.enabled
    ?? false;
  const dissolveGroupingMode = Form.useWatch('dissolveGroupingMode', form)
    ?? node.configuration.dissolve?.groupingMode
    ?? 'ALL_OR_FIELDS';
  const connectedDissolve = dissolveEnabled
    && dissolveGroupingMode === 'CONNECTED_COMPONENTS';
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
    statistics: SpatialAggregateStatistic[],
  ): SpatialAggregateConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    groupByColumns: values.groupByColumns ?? [],
    aggregations: items.map((item) => ({
      ...item,
      outputColumnName: item.outputColumnName.trim(),
    })),
    dissolve: node.configuration.dissolve != null || values.dissolveEnabled
      ? {
          enabled: Boolean(values.dissolveEnabled),
          multipart: Boolean(values.multipart),
          countOutputColumnName: values.countOutputColumnName?.trim() ?? '',
          groupingMode: values.dissolveGroupingMode ?? 'ALL_OR_FIELDS',
          summaryStatistics: statistics.map((item) => ({
            ...item,
            outputColumnName: item.outputColumnName.trim(),
          })),
        }
      : null,
  });
  const markDirty = (
    items = aggregations,
    statistics = summaryStatistics,
  ) => {
    onDirtyChange(
      fingerprint(configuration(form.getFieldsValue(), items, statistics))
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
  const updateSummaryStatistics = (items: SpatialAggregateStatistic[]) => {
    setSummaryStatistics(items);
    markDirty(aggregations, items);
  };
  const updateSummaryStatistic = (index: number, item: SpatialAggregateStatistic) => {
    updateSummaryStatistics(summaryStatistics.map((candidate, itemIndex) => (
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
      configuration: configuration(values, aggregations, summaryStatistics),
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
          dissolveEnabled: node.configuration.dissolve?.enabled ?? false,
          dissolveGroupingMode:
            node.configuration.dissolve?.groupingMode ?? 'ALL_OR_FIELDS',
          multipart: node.configuration.dissolve?.multipart ?? false,
          countOutputColumnName:
            node.configuration.dissolve?.countOutputColumnName ?? 'feature_count',
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
          validateStatus={invalidGroupColumns.length > 0
            || connectedDissolve && groupByColumns.length > 0 ? 'error' : undefined}
          help={connectedDissolve && groupByColumns.length > 0
            ? '按空间连通组时不能再配置分组字段，请移除这些字段。'
            : invalidGroupColumns.length > 0
            ? `以下字段已失效或是 Geometry：${invalidGroupColumns.join('、')}`
            : groupByColumns.length === 0
              ? connectedDissolve
                ? '将按 Polygon 相交或接触关系的传递闭包分别融合。'
                : '未选择时执行全局空间聚合。'
              : undefined}
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
        <Form.Item
          name="dissolveEnabled"
          label="Dissolve Boundaries"
          valuePropName="checked"
        >
          <Switch checkedChildren="启用" unCheckedChildren="关闭" />
        </Form.Item>
        {dissolveEnabled && (
          <>
            <Form.Item
              name="dissolveGroupingMode"
              label="Dissolve 分组方式"
            >
              <Select options={dissolveGroupingOptions} />
            </Form.Item>
            <Alert
              showIcon
              type="info"
              title="Dissolve 要求恰好一个 UNION 聚合"
              description={connectedDissolve
                ? '不使用分组字段；面要素只要相交、重叠或接触，就通过传递关系归入同一组。NULL 和 Empty Geometry 不产生结果。'
                : '空分组对应 All；选择分组字段对应 List。结果始终输出来源要素总数，可继续配置标量统计。'}
            />
            <Form.Item
              name="countOutputColumnName"
              label="来源要素计数字段"
              rules={[{ required: true, whitespace: true, message: '请输入计数字段名' }]}
            >
              <Input placeholder="feature_count" />
            </Form.Item>
            <Form.Item
              name="multipart"
              label="输出多部件 Geometry"
              valuePropName="checked"
            >
              <Switch checkedChildren="Multipart" unCheckedChildren="Singlepart" />
            </Form.Item>
          </>
        )}
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
      {dissolveEnabled && (
        <>
          <div className="canvas-processor-section-header">
            <span>
              <Typography.Text strong>标量统计</Typography.Text>
              <Typography.Text type="secondary">
                {` · ${summaryStatistics.length}/${CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS}`}
              </Typography.Text>
            </span>
            <Button
              size="small"
              icon={<PlusOutlined />}
              disabled={
                !sourceTable
                || summaryStatistics.length
                  >= CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS
              }
              onClick={() => updateSummaryStatistics([
                ...summaryStatistics,
                createStatistic(scalarColumns),
              ])}
            >
              添加统计
            </Button>
          </div>
          {summaryStatistics.length === 0 && (
            <Typography.Text type="secondary">
              未配置额外统计；来源要素总数仍会输出。
            </Typography.Text>
          )}
          <div className="canvas-processor-rule-list">
            {summaryStatistics.map((statistic, index) => {
              const sourceColumn = scalarColumns.find(
                (column) => column.name === statistic.sourceColumnName,
              );
              const sourceMissing = Boolean(statistic.sourceColumnName && !sourceColumn);
              const sourceTypeInvalid = Boolean(
                sourceColumn && !statisticAcceptsColumn(statistic.kind, sourceColumn),
              );
              const sourceInvalid = sourceMissing || sourceTypeInvalid;
              return (
                <Card
                  size="small"
                  key={statistic.statisticId || index}
                  className={`canvas-processor-rule-card${sourceInvalid ? ' is-invalid' : ''}`}
                  title={(
                    <Space size={6}>
                      <Tag color="purple">{index + 1}</Tag>
                      <span>{statistic.kind}</span>
                      {sourceInvalid && (
                        <Tag color="error">
                          {sourceMissing ? '字段已失效' : '字段类型不适用'}
                        </Tag>
                      )}
                    </Space>
                  )}
                  extra={(
                    <Space size={0}>
                      <Button
                        type="text"
                        size="small"
                        icon={<UpOutlined />}
                        aria-label={`上移 Dissolve 统计 ${index + 1}`}
                        disabled={index === 0}
                        onClick={() => {
                          const next = [...summaryStatistics];
                          const [item] = next.splice(index, 1);
                          next.splice(index - 1, 0, item);
                          updateSummaryStatistics(next);
                        }}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<DownOutlined />}
                        aria-label={`下移 Dissolve 统计 ${index + 1}`}
                        disabled={index === summaryStatistics.length - 1}
                        onClick={() => {
                          const next = [...summaryStatistics];
                          const [item] = next.splice(index, 1);
                          next.splice(index + 1, 0, item);
                          updateSummaryStatistics(next);
                        }}
                      />
                      <Button
                        type="text"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        aria-label={`删除 Dissolve 统计 ${index + 1}`}
                        onClick={() => updateSummaryStatistics(
                          summaryStatistics.filter((_, itemIndex) => itemIndex !== index),
                        )}
                      />
                    </Space>
                  )}
                >
                  <Space orientation="vertical" size={8} className="canvas-full-width">
                    <Select
                      value={statistic.kind}
                      options={statisticKindOptions}
                      onChange={(kind: SpatialAggregateStatisticKind) => updateSummaryStatistic(
                        index,
                        { ...statistic, kind },
                      )}
                    />
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={statistic.sourceColumnName || undefined}
                      status={sourceInvalid ? 'error' : undefined}
                      options={spatialColumnOptions(
                        sourceTable?.columns ?? [],
                        statistic.sourceColumnName,
                        (column) => statisticAcceptsColumn(statistic.kind, column),
                      )}
                      placeholder="统计来源字段"
                      onChange={(sourceColumnName) => updateSummaryStatistic(
                        index,
                        { ...statistic, sourceColumnName },
                      )}
                    />
                    <Input
                      value={statistic.outputColumnName}
                      placeholder="统计输出字段"
                      onChange={(event) => updateSummaryStatistic(
                        index,
                        { ...statistic, outputColumnName: event.target.value },
                      )}
                    />
                  </Space>
                </Card>
              );
            })}
          </div>
          <Typography.Text type="secondary">
            Singlepart 会拆分融合后的多部件 Geometry，并为每个部件重复该组的计数和统计值。
          </Typography.Text>
        </>
      )}
    </Space>
  );
};

export default SpatialAggregateInspector;
