import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { SettingOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Switch, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type SpatialHotSpotAnalysisSource,
  type SpatialHotSpotMultipleTesting,
  type SpatialHotSpotsConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { SpatialTemporalSlicingEditor } from '../spatialAggregationShared';
import { createSpatialTemporalSlicing } from '../spatialAggregationOptions';
import { temporalWindowLabel } from '../spatialCalendarWindow';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions, spatialUnitHelp } from '../spatialUnits';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

const numericTypes = new Set(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);
const fingerprint = (value: SpatialHotSpotsConfiguration) => JSON.stringify(value);

const SpatialHotSpotsInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialHotSpots>) => {
  const [form] = Form.useForm<SpatialHotSpotsConfiguration>();
  const [temporalOpen, setTemporalOpen] = useState(false);
  const [temporalDraft, setTemporalDraft] = useState(
    node.configuration.temporalSlicing ?? createSpatialTemporalSlicing(),
  );
  const [outputFieldsOpen, setOutputFieldsOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const analysisSource = Form.useWatch('analysisSource', form) ?? node.configuration.analysisSource;
  const multipleTesting = Form.useWatch('multipleTesting', form) ?? node.configuration.multipleTesting;
  const temporal = Form.useWatch('temporalSlicing', { form, preserve: true });
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find(table => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const geometry = spatialGeometryColumns(sourceTable)
    .find(column => column.name === pointGeometryColumnName);

  const normalize = (value: SpatialHotSpotsConfiguration): SpatialHotSpotsConfiguration => ({
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    analysisSource: value.analysisSource ?? null,
    analysisColumnName: value.analysisColumnName?.trim() || null,
    binSize: value.binSize ?? 0,
    binSizeUnit: value.binSizeUnit ?? 'METERS',
    neighborhoodDistance: value.neighborhoodDistance ?? 0,
    neighborhoodDistanceUnit: value.neighborhoodDistanceUnit ?? 'METERS',
    temporalSlicing: value.temporalSlicing ?? null,
    multipleTesting: value.multipleTesting ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    binIdColumnName: value.binIdColumnName?.trim() ?? '',
    binGeometryColumnName: value.binGeometryColumnName?.trim() ?? '',
    pointCountColumnName: value.pointCountColumnName?.trim() ?? '',
    analysisValueColumnName: value.analysisValueColumnName?.trim() ?? '',
    zScoreColumnName: value.zScoreColumnName?.trim() ?? '',
    pValueColumnName: value.pValueColumnName?.trim() ?? '',
    adjustedPValueColumnName: value.adjustedPValueColumnName?.trim() ?? '',
    confidenceBinColumnName: value.confidenceBinColumnName?.trim() ?? '',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: SpatialHotSpotsConfiguration) => {
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
  const update = <K extends keyof SpatialHotSpotsConfiguration>(
    name: K,
    value: SpatialHotSpotsConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialHotSpotsConfiguration>
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
          热点方格需要投影坐标，请先使用空间转换，不能直接用经纬度计算。
        </Typography.Text>}

      <Form.Item label={<span className="canvas-inspector-field-label">分析值<ContextHelp
        ariaLabel="热点分析值说明"
        content="点数与 ArcGIS GeoAnalytics Find Hot Spots 的点事件聚合一致；数值字段总量是平台扩展，先按方格求和再计算 Gi*。NULL 数值贡献 0。"
      /></span>} required>
        <Segmented<SpatialHotSpotAnalysisSource | ''> block value={analysisSource ?? ''}
          options={[{ value: 'POINT_COUNT', label: '点数' }, { value: 'FIELD_SUM', label: '数值字段总量' }]}
          onChange={value => value && update('analysisSource', value)} />
      </Form.Item>
      {analysisSource === 'FIELD_SUM' && <Form.Item name="analysisColumnName" label="数值分析字段"
        rules={[{ required: true, whitespace: true }]}>
        <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
          columns, form.getFieldValue('analysisColumnName') ?? '',
          column => numericTypes.has(column.fieldType),
        )} />
      </Form.Item>}

      <div className="canvas-spatial-pair-grid">
        <Form.Item label={<span className="canvas-inspector-field-label">方格大小<ContextHelp
          ariaLabel="热点方格大小说明"
          content={`方格按边长，原点固定为 (0, 0)。结果覆盖有效点外包矩形内的完整方格，包括点数为 0 的方格。${spatialUnitHelp}`}
        /></span>} required>
          <Space.Compact block>
            <Form.Item name="binSize" noStyle><InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} /></Form.Item>
            <Form.Item name="binSizeUnit" noStyle><Select options={spatialDistanceUnitOptions} style={{ width: '48%' }} /></Form.Item>
          </Space.Compact>
        </Form.Item>
        <Form.Item label={<span className="canvas-inspector-field-label">空间邻域<ContextHelp
          ariaLabel="热点空间邻域说明"
          content="以方格中心的固定距离定义二元权重并包含当前方格。距离必须严格大于方格大小，换算后与方格大小之比最多为 64。"
        /></span>} required>
          <Space.Compact block>
            <Form.Item name="neighborhoodDistance" noStyle>
              <InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} />
            </Form.Item>
            <Form.Item name="neighborhoodDistanceUnit" noStyle>
              <Select options={spatialDistanceUnitOptions} style={{ width: '48%' }} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>
      </div>

      <Form.Item label={<span className="canvas-inspector-field-label">多重检验<ContextHelp
        ariaLabel="热点多重检验说明"
        content="FDR 使用 Benjamini–Hochberg 调整每个时间片内的双侧 p 值，置信分级使用调整值；原始 p 值仍单独输出。关闭时直接使用原始 p 值分级。"
      /></span>} required>
        <Segmented<SpatialHotSpotMultipleTesting | ''> block value={multipleTesting ?? ''}
          options={[{ value: 'FDR_BH', label: 'FDR 校正' }, { value: 'NONE', label: '不校正' }]}
          onChange={value => value && update('multipleTesting', value)} />
      </Form.Item>

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
      <Typography.Text type="secondary" className="canvas-field-inline-warning">
        每个时间片独立生成同一空间范围的方格，并分别计算 Gi* 和 FDR。
      </Typography.Text>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>结果字段</Typography.Text>
          <Tag>{8 + (temporal ? 2 : 0)}</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setOutputFieldsOpen(true)}>设置</Button>
      </div>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 order_hot_spots" />
      </Form.Item>

      <Modal open={temporalOpen} width={620} title="设置热点时间切片" okText="保存草稿" cancelText="取消"
        onOk={() => { update('temporalSlicing', temporalDraft); setTemporalOpen(false); }}
        onCancel={() => setTemporalOpen(false)}>
        <SpatialTemporalSlicingEditor value={temporalDraft} columns={columns} onChange={setTemporalDraft} />
      </Modal>

      <Modal open={outputFieldsOpen} width={680} title="设置热点结果字段" okText="完成" cancelText="关闭"
        onOk={() => setOutputFieldsOpen(false)} onCancel={() => setOutputFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="binIdColumnName" label="格网 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="binGeometryColumnName" label="格网 Geometry" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="pointCountColumnName" label="点数" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="analysisValueColumnName" label="Gi* 分析值" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="zScoreColumnName" label="Gi* z-score" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="pValueColumnName" label="原始 p-value" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="adjustedPValueColumnName" label="调整后 p-value" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="confidenceBinColumnName" label="置信分级 -3…3" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
      </Modal>
    </Form>
  </Space>;
};

export default SpatialHotSpotsInspector;
