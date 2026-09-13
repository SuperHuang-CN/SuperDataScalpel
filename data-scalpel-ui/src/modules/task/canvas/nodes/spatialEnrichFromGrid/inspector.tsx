import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, Select, Space, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialEnrichFromGridConfiguration,
  type SpatialEnrichFromGridField,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';

const fingerprint = (value: SpatialEnrichFromGridConfiguration) => JSON.stringify(value);

const SpatialEnrichFromGridInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialEnrichFromGrid>) => {
  const [form] = Form.useForm<SpatialEnrichFromGridConfiguration>();
  const [fields, setFields] = useState(() => structuredClone(node.configuration.enrichFields));
  const tables = validation?.inputTables ?? [];
  const pointTableName = Form.useWatch('pointTableName', form) ?? node.configuration.pointTableName;
  const gridTableName = Form.useWatch('gridTableName', form) ?? node.configuration.gridTableName;
  const pointTable = tables.find(table => table.name === pointTableName);
  const gridTable = tables.find(table => table.name === gridTableName);
  const pointColumns = pointTable?.columns ?? [];
  const gridColumns = gridTable?.columns ?? [];

  const normalize = (
    value: SpatialEnrichFromGridConfiguration,
    nextFields = fields,
  ): SpatialEnrichFromGridConfiguration => ({
    pointTableName: value.pointTableName?.trim() ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName?.trim() ?? '',
    gridTableName: value.gridTableName?.trim() ?? '',
    gridGeometryColumnName: value.gridGeometryColumnName?.trim() ?? '',
    gridIdColumnName: value.gridIdColumnName?.trim() ?? '',
    enrichFields: nextFields.map(field => ({
      sourceColumnName: field.sourceColumnName?.trim() ?? '',
      outputColumnName: field.outputColumnName?.trim() ?? '',
    })),
    outputTableName: value.outputTableName?.trim() ?? '',
  });
  const markDirty = (
    value = form.getFieldsValue(true),
    nextFields = fields,
  ) => onDirtyChange(
    fingerprint(normalize(value, nextFields)) !== fingerprint(normalize(
      node.configuration, node.configuration.enrichFields,
    )),
  );
  const submit = (value: SpatialEnrichFromGridConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(value) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      void form.validateFields().catch(() => undefined);
      submit(form.getFieldsValue(true));
      return true;
    },
  }));

  const updateFields = (next: SpatialEnrichFromGridField[]) => {
    setFields(next);
    markDirty({ ...form.getFieldsValue(true), enrichFields: next }, next);
  };
  const moveField = (from: number, to: number) => {
    const next = [...fields];
    [next[from], next[to]] = [next[to], next[from]];
    updateFields(next);
  };
  const suggestedOutputName = (sourceColumnName: string) => {
    const pointNames = new Set(pointColumns.map(column => column.name.toLowerCase()));
    const configuredNames = new Set(fields.map(field => field.outputColumnName.toLowerCase()));
    if (!pointNames.has(sourceColumnName.toLowerCase())
      && !configuredNames.has(sourceColumnName.toLowerCase())) return sourceColumnName;
    return `${gridTableName || 'grid'}_${sourceColumnName}`;
  };
  const addField = (source?: CanvasColumnSchema) => {
    const sourceColumnName = source?.name ?? '';
    updateFields([...fields, {
      sourceColumnName,
      outputColumnName: sourceColumnName ? suggestedOutputName(sourceColumnName) : '',
    }]);
  };
  const addAllGridAttributes = () => {
    const existing = new Set(fields.map(field => field.sourceColumnName.toLowerCase()));
    const gridId = form.getFieldValue('gridIdColumnName')?.toLowerCase();
    const additions = gridColumns.filter(column => column.fieldType !== 'GEOMETRY'
      && column.name.toLowerCase() !== gridId && !existing.has(column.name.toLowerCase()));
    if (additions.length === 0) return;
    const next = [...fields];
    additions.forEach(column => {
      const pointNames = new Set(pointColumns.map(item => item.name.toLowerCase()));
      const outputNames = new Set(next.map(item => item.outputColumnName.toLowerCase()));
      const outputColumnName = !pointNames.has(column.name.toLowerCase())
        && !outputNames.has(column.name.toLowerCase())
        ? column.name : `${gridTableName || 'grid'}_${column.name}`;
      next.push({ sourceColumnName: column.name, outputColumnName });
    });
    updateFields(next);
  };
  const removeField = (index: number) => updateFields(fields.filter((_, itemIndex) => itemIndex !== index));

  const choosePointTable = (nextTableName: string) => {
    form.setFieldValue('pointTableName', nextTableName);
    const nextTable = tables.find(table => table.name === nextTableName);
    if (!form.getFieldValue('pointGeometryColumnName')) {
      const geometry = nextTable?.columns.find(column => column.fieldType === 'GEOMETRY'
        && column.geometry?.kind === 'POINT' && column.geometry.dimension === 'XY');
      if (geometry) form.setFieldValue('pointGeometryColumnName', geometry.name);
    }
    if (!form.getFieldValue('outputTableName')) {
      form.setFieldValue('outputTableName', nextTableName ? `${nextTableName}_enriched` : '');
    }
    markDirty(form.getFieldsValue(true));
  };
  const chooseGridTable = (nextTableName: string) => {
    form.setFieldValue('gridTableName', nextTableName);
    const nextTable = tables.find(table => table.name === nextTableName);
    if (!form.getFieldValue('gridGeometryColumnName')) {
      const geometry = nextTable?.columns.find(column => column.fieldType === 'GEOMETRY'
        && ['POLYGON', 'MULTIPOLYGON'].includes(column.geometry?.kind ?? '')
        && column.geometry?.dimension === 'XY');
      if (geometry) form.setFieldValue('gridGeometryColumnName', geometry.name);
    }
    if (!form.getFieldValue('gridIdColumnName')) {
      const id = nextTable?.columns.find(column => column.name.toLowerCase() === 'bin_id')
        ?? nextTable?.columns.find(column => column.fieldType !== 'GEOMETRY'
          && (column.name.toLowerCase() === 'grid_id' || column.name.toLowerCase() === 'id'));
      if (id) form.setFieldValue('gridIdColumnName', id.name);
    }
    markDirty(form.getFieldsValue(true));
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialEnrichFromGridConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={normalize(node.configuration, node.configuration.enrichFields)}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <Typography.Text strong>Point 来源</Typography.Text>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="pointTableName" label="Point 表" required>
          <Select showSearch optionFilterProp="label" placeholder="选择上游 Point 表"
            options={spatialTableOptions(tables, pointTableName)} onChange={choosePointTable} />
        </Form.Item>
        <Form.Item name="pointGeometryColumnName" label="Point Geometry" required>
          <Select showSearch optionFilterProp="label" placeholder="选择 XY Point"
            options={spatialColumnOptions(
              pointColumns, form.getFieldValue('pointGeometryColumnName') ?? '',
              column => column.fieldType === 'GEOMETRY' && column.geometry?.kind === 'POINT'
                && column.geometry.dimension === 'XY',
            )} />
        </Form.Item>
      </div>

      <Typography.Text strong>多变量格网</Typography.Text>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="gridTableName" label="格网表" required>
          <Select showSearch optionFilterProp="label" placeholder="选择上游多变量格网"
            options={spatialTableOptions(tables, gridTableName)} onChange={chooseGridTable} />
        </Form.Item>
        <Form.Item name="gridGeometryColumnName" label="格网 Geometry" required>
          <Select showSearch optionFilterProp="label" placeholder="选择 XY Polygon"
            options={spatialColumnOptions(
              gridColumns, form.getFieldValue('gridGeometryColumnName') ?? '',
              column => column.fieldType === 'GEOMETRY'
                && ['POLYGON', 'MULTIPOLYGON'].includes(column.geometry?.kind ?? '')
                && column.geometry?.dimension === 'XY',
            )} />
        </Form.Item>
      </div>
      <Form.Item label={<span className="canvas-inspector-field-label">格网唯一标识<ContextHelp
        ariaLabel="格网唯一标识说明"
        content="应选择 Build Multi-Variable Grid 产生的格网 ID。点位于共享边界或格网重叠时，系统按该字段的字符串顺序稳定选择一个格网，保证每个输入 Point 只输出一行。"
      /></span>} name="gridIdColumnName" required>
        <Select showSearch optionFilterProp="label" placeholder="例如 bin_id"
          options={spatialColumnOptions(
            gridColumns, form.getFieldValue('gridIdColumnName') ?? '',
            column => column.fieldType !== 'GEOMETRY',
          )} />
      </Form.Item>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>丰富字段</Typography.Text>
          <Typography.Text type="secondary">{fields.length} 项</Typography.Text></Space>
        <Space size={4}>
          <Button size="small" disabled={!gridTable || gridColumns.length === 0}
            onClick={addAllGridAttributes}>添加全部属性</Button>
          <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => addField()}>
            添加字段
          </Button>
        </Space>
      </div>
      <div className="canvas-processor-operation-list">
        {fields.map((field, index) => {
          const invalid = Boolean(validation?.issues.some(issue => issue.severity === 'ERROR'
            && issue.path?.startsWith(`configuration.enrichFields[${index}]`)));
          return <div className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
            key={`${index}-${field.sourceColumnName}`}>
            <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
            <Select size="small" showSearch optionFilterProp="label" value={field.sourceColumnName || undefined}
              placeholder="格网字段" style={{ minWidth: 128, flex: 1 }} options={spatialColumnOptions(
                gridColumns, field.sourceColumnName, column => column.fieldType !== 'GEOMETRY',
              )} onChange={sourceColumnName => {
                const next = [...fields];
                next[index] = { sourceColumnName, outputColumnName: field.outputColumnName
                  || suggestedOutputName(sourceColumnName) };
                updateFields(next);
              }} />
            <Input size="small" value={field.outputColumnName} placeholder="结果字段名"
              style={{ minWidth: 128, flex: 1 }} onChange={event => {
                const next = [...fields];
                next[index] = { ...field, outputColumnName: event.target.value };
                updateFields(next);
              }} />
            <Space size={0}>
              <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                aria-label={`上移丰富字段 ${index + 1}`} onClick={() => moveField(index, index - 1)} />
              <Button type="text" size="small" icon={<DownOutlined />} disabled={index === fields.length - 1}
                aria-label={`下移丰富字段 ${index + 1}`} onClick={() => moveField(index, index + 1)} />
              <Button type="text" danger size="small" icon={<DeleteOutlined />}
                aria-label={`删除丰富字段 ${index + 1}`} onClick={() => removeField(index)} />
            </Space>
          </div>;
        })}
        {fields.length === 0 && <Typography.Text type="secondary">
          显式选择要回填的格网属性；不会自动跟随格网 Schema 增加字段。
        </Typography.Text>}
      </div>

      <Form.Item name="outputTableName" label="输出表名" required
        rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 incidents_enriched" />
      </Form.Item>
      <Typography.Text type="secondary">
        未命中格网的 Point 仍保留，丰富字段为 NULL；本节点不重新计算多变量格网。
      </Typography.Text>
    </Form>
  </Space>;
};

export default SpatialEnrichFromGridInspector;
