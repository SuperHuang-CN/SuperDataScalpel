import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Select, Space, Switch, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialGroupByProximityAttributeCondition,
  type SpatialGroupByProximityConfiguration,
  type SpatialGroupByProximityTemporalCondition,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions } from '../spatialUnits';

const fingerprint = (value: SpatialGroupByProximityConfiguration) => JSON.stringify(value);
const temporalColumn = (column: CanvasColumnSchema) => (
  ['DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ'].includes(column.fieldType)
);
const numericColumn = (column: CanvasColumnSchema) => (
  ['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']
    .includes(column.fieldType)
);
const defaultTemporal = (): SpatialGroupByProximityTemporalCondition => ({
  relationship: 'INTERSECTS',
  startColumnName: '',
  endColumnName: null,
  nearDistance: 10,
  nearDistanceUnit: 'MINUTES',
});

const SpatialGroupByProximityInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialGroupByProximity>) => {
  const [form] = Form.useForm<SpatialGroupByProximityConfiguration>();
  const [temporalEnabled, setTemporalEnabled] = useState(Boolean(node.configuration.temporalCondition));
  const [attributeConditions, setAttributeConditions] = useState(
    () => structuredClone(node.configuration.attributeConditions),
  );
  const tables = validation?.inputTables ?? [];
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? node.configuration.sourceTableName;
  const spatialRelationship = Form.useWatch('spatialRelationship', form)
    ?? node.configuration.spatialRelationship;
  const temporalRelationship = Form.useWatch(
    ['temporalCondition', 'relationship'], form,
  ) ?? node.configuration.temporalCondition?.relationship;
  const sourceTable = tables.find(table => table.name === sourceTableName);
  const sourceColumns = sourceTable?.columns ?? [];

  const normalize = (
    value: SpatialGroupByProximityConfiguration,
    attributes = attributeConditions,
    includeTemporal = temporalEnabled,
  ): SpatialGroupByProximityConfiguration => ({
    sourceTableName: value.sourceTableName?.trim() ?? '',
    geometryColumnName: value.geometryColumnName?.trim() ?? '',
    spatialRelationship: value.spatialRelationship ?? null,
    spatialNearDistance: value.spatialNearDistance ?? null,
    spatialNearDistanceUnit: value.spatialNearDistanceUnit ?? null,
    temporalCondition: includeTemporal ? {
      relationship: value.temporalCondition?.relationship ?? null,
      startColumnName: value.temporalCondition?.startColumnName?.trim() ?? '',
      endColumnName: value.temporalCondition?.endColumnName?.trim() || null,
      nearDistance: value.temporalCondition?.nearDistance ?? null,
      nearDistanceUnit: value.temporalCondition?.nearDistanceUnit ?? null,
    } : null,
    attributeConditions: attributes.map(condition => ({
      columnName: condition.columnName?.trim() ?? '',
      relationship: condition.relationship ?? null,
      maximumDifference: condition.maximumDifference ?? null,
    })),
    groupIdColumnName: value.groupIdColumnName?.trim() ?? '',
    outputTableName: value.outputTableName?.trim() ?? '',
  });
  const markDirty = (
    value = form.getFieldsValue(true),
    attributes = attributeConditions,
    includeTemporal = temporalEnabled,
  ) => onDirtyChange(
    fingerprint(normalize(value, attributes, includeTemporal))
      !== fingerprint(normalize(
        { ...node.configuration, temporalCondition: node.configuration.temporalCondition
          ?? defaultTemporal() },
        node.configuration.attributeConditions,
        Boolean(node.configuration.temporalCondition),
      )),
  );
  const submit = (value: SpatialGroupByProximityConfiguration) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: normalize(value),
    });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      void form.validateFields().catch(() => undefined);
      submit(form.getFieldsValue(true));
      return true;
    },
  }));

  const updateAttributes = (next: SpatialGroupByProximityAttributeCondition[]) => {
    setAttributeConditions(next);
    markDirty(form.getFieldsValue(true), next);
  };
  const moveAttribute = (from: number, to: number) => {
    const next = [...attributeConditions];
    [next[from], next[to]] = [next[to], next[from]];
    updateAttributes(next);
  };
  const chooseSourceTable = (nextTableName: string) => {
    form.setFieldValue('sourceTableName', nextTableName);
    const table = tables.find(item => item.name === nextTableName);
    if (!form.getFieldValue('geometryColumnName')) {
      const geometry = table?.columns.find(column => column.fieldType === 'GEOMETRY'
        && column.geometry?.dimension === 'XY'
        && !['GEOMETRY', 'GEOMETRYCOLLECTION'].includes(column.geometry.kind));
      if (geometry) form.setFieldValue('geometryColumnName', geometry.name);
    }
    if (!form.getFieldValue('outputTableName')) {
      form.setFieldValue('outputTableName', nextTableName ? `${nextTableName}_groups` : '');
    }
    markDirty(form.getFieldsValue(true));
  };
  const toggleTemporal = (checked: boolean) => {
    setTemporalEnabled(checked);
    if (checked && !form.getFieldValue('temporalCondition')) {
      form.setFieldValue('temporalCondition', defaultTemporal());
    }
    markDirty(form.getFieldsValue(true), attributeConditions, checked);
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation}
      unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialGroupByProximityConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={{
        ...node.configuration,
        temporalCondition: node.configuration.temporalCondition ?? defaultTemporal(),
      }}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <Typography.Text strong>分组来源</Typography.Text>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="sourceTableName" label="来源表" required>
          <Select showSearch optionFilterProp="label" placeholder="选择上游空间表"
            options={spatialTableOptions(tables, sourceTableName)} onChange={chooseSourceTable} />
        </Form.Item>
        <Form.Item name="geometryColumnName" label="Geometry" required>
          <Select showSearch optionFilterProp="label" placeholder="选择 XY Geometry"
            options={spatialColumnOptions(
              sourceColumns, form.getFieldValue('geometryColumnName') ?? '',
              column => column.fieldType === 'GEOMETRY' && column.geometry?.dimension === 'XY'
                && !['GEOMETRY', 'GEOMETRYCOLLECTION'].includes(column.geometry.kind),
            )} />
        </Form.Item>
      </div>

      <Form.Item name="spatialRelationship" required label={<span
        className="canvas-inspector-field-label">空间关系<ContextHelp
          ariaLabel="邻近分组空间关系说明"
          content="相交适用于 Point、Line、Polygon；接触仅适用于 Line、Polygon。平面邻近要求投影 CRS，测地邻近要求 EPSG:4326 XY。满足关系的要素先形成边，再按传递闭包得到连通组。"
        /></span>}>
        <Select options={[
          { value: 'INTERSECTS', label: '相交 Intersects' },
          { value: 'TOUCHES', label: '接触 Touches' },
          { value: 'NEAR_PLANAR', label: '平面邻近 Near Planar' },
          { value: 'NEAR_GEODESIC', label: '测地邻近 Near Geodesic' },
        ]} />
      </Form.Item>
      {spatialRelationship?.startsWith('NEAR') && <div className="canvas-spatial-pair-grid">
        <Form.Item name="spatialNearDistance" label="邻近距离" required>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="spatialNearDistanceUnit" label="距离单位" required>
          <Select options={spatialDistanceUnitOptions.filter(option => (
            spatialRelationship !== 'NEAR_GEODESIC' || option.value !== 'SOURCE_CRS_UNIT'
          ))} />
        </Form.Item>
      </div>}

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>时间关系</Typography.Text><ContextHelp
          ariaLabel="邻近分组时间关系说明"
          content="可选。瞬时只选开始字段；时间范围再选结束字段。空间、时间和全部属性关系必须同时满足，才会连接两个要素。月和年按 Spark 会话时区的日历区间推进。"
        /></Space>
        <Switch size="small" checked={temporalEnabled} onChange={toggleTemporal}
          aria-label="启用时间关系" />
      </div>
      {temporalEnabled && <>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name={['temporalCondition', 'relationship']} label="时间关系" required>
            <Select options={[
              { value: 'INTERSECTS', label: '时间相交' },
              { value: 'NEAR', label: '时间邻近' },
            ]} />
          </Form.Item>
          <Form.Item name={['temporalCondition', 'startColumnName']}
            label="开始 / 瞬时时间" required>
            <Select showSearch optionFilterProp="label" placeholder="选择时间字段"
              options={spatialColumnOptions(
                sourceColumns,
                form.getFieldValue(['temporalCondition', 'startColumnName']) ?? '',
                temporalColumn,
              )} />
          </Form.Item>
        </div>
        <Form.Item name={['temporalCondition', 'endColumnName']} label="结束时间（可选）">
          <Select allowClear showSearch optionFilterProp="label" placeholder="不选表示瞬时"
            options={spatialColumnOptions(
              sourceColumns,
              form.getFieldValue(['temporalCondition', 'endColumnName']) ?? '',
              temporalColumn,
            )} />
        </Form.Item>
        {temporalRelationship === 'NEAR' && <div className="canvas-spatial-pair-grid">
          <Form.Item name={['temporalCondition', 'nearDistance']}
            label="时间邻近距离" required>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name={['temporalCondition', 'nearDistanceUnit']}
            label="时间单位" required>
            <Select options={[
              ['MILLISECONDS', '毫秒'], ['SECONDS', '秒'], ['MINUTES', '分钟'],
              ['HOURS', '小时'], ['DAYS', '天'], ['WEEKS', '周'],
              ['MONTHS', '月（日历）'], ['YEARS', '年（日历）'],
            ].map(([value, label]) => ({ value, label }))} />
          </Form.Item>
        </div>}
      </>}

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>属性关系</Typography.Text>
          <Typography.Text type="secondary">{attributeConditions.length} 项</Typography.Text>
          <ContextHelp ariaLabel="邻近分组属性关系说明"
            content="当前提供同值和数值绝对差两种对称关系，覆盖常见分区和精度容差场景。多项属性关系全部 AND；NULL 不与任何值匹配。任意 ArcGIS 表达式尚未开放。" />
        </Space>
        <Button size="small" type="primary" icon={<PlusOutlined />}
          disabled={attributeConditions.length >= 8}
          onClick={() => updateAttributes([...attributeConditions, {
            columnName: '', relationship: 'EQUALS', maximumDifference: null,
          }])}>添加关系</Button>
      </div>
      <div className="canvas-processor-operation-list">
        {attributeConditions.map((condition, index) => {
          const invalid = Boolean(validation?.issues.some(issue => issue.severity === 'ERROR'
            && issue.path?.startsWith(`configuration.attributeConditions[${index}]`)));
          return <div className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
            key={`${index}-${condition.columnName}`}>
            <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
            <Select size="small" showSearch optionFilterProp="label"
              value={condition.columnName || undefined} placeholder="字段"
              style={{ minWidth: 116, flex: 1 }}
              options={spatialColumnOptions(
                sourceColumns, condition.columnName,
                column => column.fieldType !== 'GEOMETRY'
                  && (condition.relationship !== 'ABSOLUTE_DIFFERENCE_AT_MOST'
                    || numericColumn(column)),
              )} onChange={columnName => {
                const next = [...attributeConditions];
                next[index] = { ...condition, columnName };
                updateAttributes(next);
              }} />
            <Select size="small" value={condition.relationship ?? undefined}
              style={{ minWidth: 112, flex: 1 }} options={[
                { value: 'EQUALS', label: '同值' },
                { value: 'ABSOLUTE_DIFFERENCE_AT_MOST', label: '绝对差 ≤' },
              ]} onChange={relationship => {
                const next = [...attributeConditions];
                next[index] = { ...condition, relationship };
                updateAttributes(next);
              }} />
            {condition.relationship === 'ABSOLUTE_DIFFERENCE_AT_MOST'
              && <InputNumber size="small" min={0} value={condition.maximumDifference}
                placeholder="阈值" style={{ width: 88 }} onChange={maximumDifference => {
                  const next = [...attributeConditions];
                  next[index] = { ...condition, maximumDifference };
                  updateAttributes(next);
                }} />}
            <Space size={0}>
              <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                aria-label={`上移属性关系 ${index + 1}`}
                onClick={() => moveAttribute(index, index - 1)} />
              <Button type="text" size="small" icon={<DownOutlined />}
                disabled={index === attributeConditions.length - 1}
                aria-label={`下移属性关系 ${index + 1}`}
                onClick={() => moveAttribute(index, index + 1)} />
              <Button type="text" danger size="small" icon={<DeleteOutlined />}
                aria-label={`删除属性关系 ${index + 1}`}
                onClick={() => updateAttributes(attributeConditions.filter(
                  (_, itemIndex) => itemIndex !== index,
                ))} />
            </Space>
          </div>;
        })}
        {attributeConditions.length === 0 && <Typography.Text type="secondary">
          不配置时，仅使用空间关系和已启用的时间关系分组。
        </Typography.Text>}
      </div>

      <div className="canvas-spatial-pair-grid">
        <Form.Item name="groupIdColumnName" label="分组 ID 字段" required>
          <Input placeholder="group_id" />
        </Form.Item>
        <Form.Item name="outputTableName" label="输出表名" required
          rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 road_groups" />
        </Form.Item>
      </div>
      <Typography.Text type="secondary">
        每个来源要素恰好保留一行；孤立要素也拥有自己的组。分组 ID 只表示成员关系，
        不保证连续，也不保证多次运行得到相同数值。
      </Typography.Text>
    </Form>
  </Space>;
};

export default SpatialGroupByProximityInspector;
