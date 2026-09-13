import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, DownOutlined, PlusOutlined, SettingOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Switch, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_DENSITY_MAX_FIELDS,
  CanvasNodeType,
  type SpatialDensityBinShape,
  type SpatialDensityConfiguration,
  type SpatialDensityField,
  type SpatialDensityWeighting,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { SpatialTemporalSlicingEditor } from '../spatialAggregationShared';
import { createSpatialTemporalSlicing } from '../spatialAggregationOptions';
import { temporalWindowLabel } from '../spatialCalendarWindow';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialAreaUnitOptions, spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

const numericTypes = new Set(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);
const fingerprint = (value: SpatialDensityConfiguration) => JSON.stringify(value);

const SpatialDensityInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialDensity>) => {
  const [form] = Form.useForm<SpatialDensityConfiguration>();
  const [fieldsOpen, setFieldsOpen] = useState(false);
  const [fieldDrafts, setFieldDrafts] = useState<SpatialDensityField[]>([]);
  const [temporalOpen, setTemporalOpen] = useState(false);
  const [temporalDraft, setTemporalDraft] = useState(
    node.configuration.temporalSlicing ?? createSpatialTemporalSlicing(),
  );
  const [outputFieldsOpen, setOutputFieldsOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const weighting = Form.useWatch('weighting', form) ?? node.configuration.weighting;
  const binShape = Form.useWatch('binShape', form) ?? node.configuration.binShape;
  const fields = Form.useWatch('fields', { form, preserve: true }) ?? [];
  const temporal = Form.useWatch('temporalSlicing', { form, preserve: true });
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const geometry = spatialGeometryColumns(sourceTable)
    .find((column) => column.name === pointGeometryColumnName);

  const normalize = (value: SpatialDensityConfiguration): SpatialDensityConfiguration => ({
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    fields: value.fields ?? [],
    weighting: value.weighting ?? null,
    binShape: value.binShape ?? null,
    binSize: value.binSize ?? 0,
    binSizeUnit: value.binSizeUnit ?? 'METERS',
    radius: value.radius ?? 0,
    radiusUnit: value.radiusUnit ?? 'METERS',
    areaUnit: value.areaUnit ?? 'SQUARE_KILOMETERS',
    temporalSlicing: value.temporalSlicing ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    binIdColumnName: value.binIdColumnName?.trim() ?? '',
    binGeometryColumnName: value.binGeometryColumnName?.trim() ?? '',
    countDensityColumnName: value.countDensityColumnName?.trim() ?? '',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: SpatialDensityConfiguration) => {
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
  const update = <K extends keyof SpatialDensityConfiguration>(
    name: K,
    value: SpatialDensityConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };
  const updateField = (index: number, value: SpatialDensityField) => {
    setFieldDrafts(fieldDrafts.map((item, itemIndex) => itemIndex === index ? value : item));
  };
  const moveField = (from: number, to: number) => {
    const next = [...fieldDrafts];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    setFieldDrafts(next);
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialDensityConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={normalize(node.configuration)}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="sourceTableName" label="来源点表" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)} />
        </Form.Item>
        <Form.Item name="pointGeometryColumnName" label="Point Geometry" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns, pointGeometryColumnName,
            column => column.fieldType === 'GEOMETRY' && column.geometry?.kind === 'POINT'
              && column.geometry.dimension === 'XY',
          )} />
        </Form.Item>
      </div>
      {geometry?.geometry?.crs.authority === 'EPSG' && geometry.geometry.crs.code === 4326 &&
        <Typography.Text type="warning" className="canvas-field-inline-warning">
          密度格网需要投影坐标，请先使用空间转换，不能直接用经纬度计算。
        </Typography.Text>}

      <Form.Item label={<span className="canvas-inspector-field-label">密度方法<ContextHelp
        ariaLabel="密度方法说明"
        content="Uniform：搜索半径内每个点等权贡献；Kernel：使用四次核，越靠近格网中心贡献越高，半径边界贡献为 0。两者都始终输出点数密度，可另外配置数值数量字段。"
      /></span>} required>
        <Segmented<SpatialDensityWeighting | ''> block value={weighting ?? ''}
          options={[{ value: 'UNIFORM', label: 'Uniform' }, { value: 'KERNEL', label: 'Kernel' }]}
          onChange={value => value && update('weighting', value)} />
      </Form.Item>
      <Form.Item label="格网形状" required>
        <Segmented<SpatialDensityBinShape | ''> block value={binShape ?? ''}
          options={[{ value: 'SQUARE', label: '方格' }, { value: 'HEXAGON', label: '六边形' }]}
          onChange={value => value && update('binShape', value)} />
      </Form.Item>

      <div className="canvas-spatial-pair-grid">
        <Form.Item label={<span className="canvas-inspector-field-label">格网大小<ContextHelp
          ariaLabel="密度格网大小说明"
          content={`方格按边长，六边形按对边距离。格网原点固定为 (0, 0)，仅输出搜索半径内有点贡献的格网。${spatialUnitHelp}`}
        /></span>} required>
          <Space.Compact block>
            <Form.Item name="binSize" noStyle><InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} /></Form.Item>
            <Form.Item name="binSizeUnit" noStyle><Select options={spatialDistanceUnitOptions} style={{ width: '48%' }} /></Form.Item>
          </Space.Compact>
        </Form.Item>
        <Form.Item label={<span className="canvas-inspector-field-label">搜索半径<ContextHelp
          ariaLabel="密度搜索半径说明"
          content="半径必须严格大于格网大小，换算后的半径/格网大小最多为 512。半径越大，每个点展开的候选格网越多，执行成本越高。"
        /></span>} required>
          <Space.Compact block>
            <Form.Item name="radius" noStyle><InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} /></Form.Item>
            <Form.Item name="radiusUnit" noStyle><Select options={spatialDistanceUnitOptions} style={{ width: '48%' }} /></Form.Item>
          </Space.Compact>
        </Form.Item>
      </div>
      <Form.Item name="areaUnit" label="密度面积单位" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" options={spatialAreaUnitOptions} />
      </Form.Item>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>数量字段</Typography.Text><Tag>{fields.length}</Tag></Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => {
          setFieldDrafts(fields.map(field => ({ ...field })));
          setFieldsOpen(true);
        }}>设置</Button>
      </div>
      <Typography.Text type="secondary" className="canvas-field-inline-warning">
        点数密度始终输出；数量字段为可选，NULL 值不贡献该字段。
      </Typography.Text>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>时间切片</Typography.Text>
          <Typography.Text type="secondary">{temporal ? temporalWindowLabel(temporal) : '未启用'}</Typography.Text>
        </Space>
        <Space size={4}>
          <Switch size="small" checked={Boolean(temporal)} onChange={checked =>
            update('temporalSlicing', checked ? temporalDraft : null)} />
          <Button size="small" disabled={!temporal} onClick={() => {
            setTemporalDraft(structuredClone(form.getFieldValue('temporalSlicing') ?? createSpatialTemporalSlicing()));
            setTemporalOpen(true);
          }}>设置</Button>
        </Space>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>结果字段</Typography.Text>
          <Tag>{fields.length + 3 + (temporal ? 2 : 0)}</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setOutputFieldsOpen(true)}>设置</Button>
      </div>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 order_density" />
      </Form.Item>

      <Modal open={fieldsOpen} width={820} title="设置数量字段" okText="保存字段草稿" cancelText="取消"
        onOk={() => { update('fields', fieldDrafts); setFieldsOpen(false); }}
        onCancel={() => setFieldsOpen(false)}>
        <div className="canvas-spatial-modal-toolbar">
          <Typography.Text type="secondary">每个字段输出一列 DOUBLE 密度；不展示或记录数据值。</Typography.Text>
          <Button type="primary" size="small" icon={<PlusOutlined />}
            disabled={fieldDrafts.length >= CANVAS_SPATIAL_DENSITY_MAX_FIELDS}
            onClick={() => setFieldDrafts([...fieldDrafts, {
              fieldId: crypto.randomUUID(), sourceColumnName: '', outputColumnName: '',
            }])}>添加字段</Button>
        </div>
        <Space orientation="vertical" size={6} style={{ width: '100%' }}>
          {fieldDrafts.map((field, index) => <div className="canvas-spatial-statistic-row" key={field.fieldId}>
            <Select showSearch optionFilterProp="label" value={field.sourceColumnName || undefined}
              placeholder="数值来源字段" options={spatialColumnOptions(
                columns, field.sourceColumnName,
                column => numericTypes.has(column.fieldType),
              )} onChange={sourceColumnName => updateField(index, {
                ...field,
                sourceColumnName,
                outputColumnName: field.outputColumnName || `${sourceColumnName}_density`,
              })} />
            <Input value={field.outputColumnName} placeholder="密度输出字段"
              onChange={event => updateField(index, { ...field, outputColumnName: event.target.value })} />
            <Space size={0}>
              <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                aria-label={`上移数量字段 ${index + 1}`} onClick={() => moveField(index, index - 1)} />
              <Button type="text" size="small" icon={<DownOutlined />} disabled={index === fieldDrafts.length - 1}
                aria-label={`下移数量字段 ${index + 1}`} onClick={() => moveField(index, index + 1)} />
              <Button type="text" danger size="small" icon={<DeleteOutlined />}
                aria-label={`删除数量字段 ${index + 1}`} onClick={() => setFieldDrafts(
                  fieldDrafts.filter((_, itemIndex) => itemIndex !== index),
                )} />
            </Space>
          </div>)}
        </Space>
      </Modal>

      <Modal open={temporalOpen} width={620} title="设置密度时间切片" okText="保存草稿" cancelText="取消"
        onOk={() => { update('temporalSlicing', temporalDraft); setTemporalOpen(false); }}
        onCancel={() => setTemporalOpen(false)}>
        <SpatialTemporalSlicingEditor value={temporalDraft} columns={columns} onChange={setTemporalDraft} />
      </Modal>

      <Modal open={outputFieldsOpen} width={620} title="设置密度结果字段" okText="完成" cancelText="关闭"
        onOk={() => setOutputFieldsOpen(false)} onCancel={() => setOutputFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="binIdColumnName" label="格网 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="binGeometryColumnName" label="格网 Geometry" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
        <Form.Item name="countDensityColumnName" label="点数密度" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
      </Modal>
    </Form>
  </Space>;
};

export default SpatialDensityInspector;
