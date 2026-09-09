import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, DownOutlined, PlusOutlined, SettingOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type TrackMotionMetric,
  type TrackMotionStatisticsConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { TrackBoundaryEditor } from '../trackShared';
import { trackDistanceUnitOptions, trackDurationUnitOptions } from '../trackOptions';
import { createMotionWindowOptions, selectedMotionGroups } from './windowOptions';
import MotionWindowEditor from './windowEditor';

const metricKinds = [
  'DISTANCE', 'ELEVATION_CHANGE', 'DURATION', 'SPEED', 'ACCELERATION', 'BEARING', 'SLOPE', 'IDLE',
] as const;
type MetricKind = TrackMotionMetric['kind'];
const metricKindOptions = metricKinds.map((value) => ({ value, label: value }));
const speedUnits = [
  { value: 'METERS_PER_SECOND', label: '米/秒' },
  { value: 'KILOMETERS_PER_HOUR', label: '千米/小时' },
  { value: 'FEET_PER_SECOND', label: '英尺/秒' },
  { value: 'MILES_PER_HOUR', label: '英里/小时' },
  { value: 'KNOTS', label: '节' },
];
const accelerationUnits = [
  { value: 'METERS_PER_SECOND_SQUARED', label: '米/秒²' },
  { value: 'FEET_PER_SECOND_SQUARED', label: '英尺/秒²' },
];
const outputNameByKind: Record<MetricKind, string> = {
  DISTANCE: 'distance', ELEVATION_CHANGE: 'elevation_change', DURATION: 'duration',
  SPEED: 'speed', ACCELERATION: 'acceleration', BEARING: 'bearing', SLOPE: 'slope', IDLE: 'is_idle',
};

const createMetric = (kind: MetricKind): TrackMotionMetric => {
  const base = { kind, metricId: crypto.randomUUID(), outputColumnName: outputNameByKind[kind] };
  switch (kind) {
    case 'DISTANCE': case 'ELEVATION_CHANGE': return { ...base, kind, outputUnit: 'METERS' };
    case 'DURATION': return { ...base, kind, outputUnit: 'SECONDS' };
    case 'SPEED': return { ...base, kind, outputUnit: 'METERS_PER_SECOND' };
    case 'ACCELERATION': return { ...base, kind, outputUnit: 'METERS_PER_SECOND_SQUARED' };
    case 'BEARING': return { ...base, kind, outputUnit: 'DEGREES' };
    case 'SLOPE': return { ...base, kind, outputUnit: 'PERCENT' };
    case 'IDLE': return { ...base, kind, outputUnit: null };
  }
};

const changeMetricKind = (metric: TrackMotionMetric, kind: MetricKind): TrackMotionMetric => ({
  ...createMetric(kind),
  metricId: metric.metricId,
  outputColumnName: metric.outputColumnName || outputNameByKind[kind],
});

const unitOptions = (metric: TrackMotionMetric) => {
  if (metric.kind === 'DISTANCE' || metric.kind === 'ELEVATION_CHANGE') return trackDistanceUnitOptions;
  if (metric.kind === 'DURATION') return trackDurationUnitOptions;
  if (metric.kind === 'SPEED') return speedUnits;
  if (metric.kind === 'ACCELERATION') return accelerationUnits;
  if (metric.kind === 'BEARING') return [{ value: 'DEGREES', label: '度' }];
  if (metric.kind === 'SLOPE') return [{ value: 'PERCENT', label: '百分比' }];
  return [];
};

const multipleColumnOptions = (columns: CanvasColumnSchema[], selected: string[]) => {
  const names = new Set(columns.map((column) => column.name));
  return [
    ...selected.filter((name) => !names.has(name)).map((name) => ({
      value: name, label: `${name}（已失效）`, disabled: true,
    })),
    ...columns.filter((column) => column.fieldType !== 'GEOMETRY').map((column) => ({
      value: column.name, label: `${column.name} · ${column.fieldType}`,
    })),
  ];
};

const fingerprint = (value: TrackMotionStatisticsConfiguration) => JSON.stringify(value);

const TrackMotionStatisticsInspector = ({
  node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackMotionStatistics>) => {
  const [form] = Form.useForm<TrackMotionStatisticsConfiguration>();
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const [metricsOpen, setMetricsOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const timeColumnName = Form.useWatch('timeColumnName', form) ?? '';
  const trackIdColumns = Form.useWatch('trackIdColumns', form) ?? [];
  const boundaries = Form.useWatch('boundaries', { form, preserve: true }) ?? node.configuration.boundaries;
  const metrics = Form.useWatch('metrics', { form, preserve: true }) ?? [];
  const semantics = Form.useWatch('motionSemantics', { form, preserve: true }) ?? node.configuration.motionSemantics ?? 'LEGACY_LAG';
  const windowOptions = Form.useWatch('windowOptions', { form, preserve: true }) ?? node.configuration.windowOptions;
  const windowMode = semantics === 'OBSERVATION_WINDOW';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const hasIdle = windowMode ? selectedMotionGroups(windowOptions).includes('IDLE') : metrics.some((metric) => metric.kind === 'IDLE');

  const normalize = (value: TrackMotionStatisticsConfiguration): TrackMotionStatisticsConfiguration => ({
    motionSemantics: value.motionSemantics ?? 'LEGACY_LAG',
    windowOptions: value.windowOptions ?? null,
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    trackIdColumns: value.trackIdColumns ?? [],
    timeColumnName: value.timeColumnName ?? '',
    distanceMethod: value.distanceMethod ?? null,
    boundaries: value.boundaries ?? node.configuration.boundaries,
    historyPoints: value.historyPoints ?? 1,
    idleDistanceThreshold: value.idleDistanceThreshold ?? null,
    idleDistanceThresholdUnit: value.idleDistanceThresholdUnit ?? null,
    metrics: value.metrics ?? [],
    outputTableName: value.outputTableName?.trim() ?? '',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: TrackMotionStatisticsConfiguration) => {
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
  const updateField = <K extends keyof TrackMotionStatisticsConfiguration>(
    name: K,
    value: TrackMotionStatisticsConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };
  const updateMetric = (index: number, metric: TrackMotionMetric) => updateField(
    'metrics', metrics.map((item, itemIndex) => itemIndex === index ? metric : item),
  );
  const moveMetric = (from: number, to: number) => {
    const next = [...metrics];
    const [metric] = next.splice(from, 1);
    next.splice(to, 0, metric);
    updateField('metrics', next);
  };
  const boundaryCount = [boundaries.maximumTimeGap, boundaries.maximumDistanceGap, boundaries.fixedTimeBoundary]
    .filter((value) => value != null).length;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<TrackMotionStatisticsConfiguration> form={form} layout="vertical" autoComplete="off"
      initialValues={normalize(node.configuration)} onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}>
      <Form.Item label="计算语义"><Select aria-label="计算语义" value={semantics} options={[
        { value: 'OBSERVATION_WINDOW', label: '观测历史窗口' }, { value: 'LEGACY_LAG', label: '历史点偏移（旧版）' },
      ]} onChange={(value: 'OBSERVATION_WINDOW' | 'LEGACY_LAG') => {
        if (value === semantics) return;
        Modal.confirm({ title: '切换运动统计语义？', content: '窗口统计与旧版两点比较不同；旧指标及新窗口配置分别保留，不自动转换。新窗口默认选择距离和速度组。',
          okText: '确认切换', cancelText: '取消', onOk: () => {
            if (value === 'OBSERVATION_WINDOW' && !form.getFieldValue('windowOptions')) form.setFieldValue('windowOptions', createMotionWindowOptions());
            updateField('motionSemantics', value);
          } });
      }} /></Form.Item>
      <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" disabled={!validation}
          options={spatialTableOptions(tables, sourceTableName)} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="pointGeometryColumnName" label="点 Geometry" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns, pointGeometryColumnName,
            (column) => column.fieldType === 'GEOMETRY' && column.geometry?.kind === 'POINT',
          )} />
        </Form.Item>
        <Form.Item name="timeColumnName" label="时间字段" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns, timeColumnName, (column) => column.fieldType === 'TIMESTAMP',
          )} />
        </Form.Item>
      </div>
      <Form.Item name="trackIdColumns" label="轨迹标识" rules={[{ required: true, type: 'array', min: 1 }]}>
        <Select mode="multiple" maxCount={8} showSearch optionFilterProp="label"
          options={multipleColumnOptions(columns, trackIdColumns)} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="distanceMethod" label="距离方法" rules={[{ required: true }]}>
          <Segmented block options={[{ value: 'PLANAR', label: '平面' }, { value: 'GEODESIC', label: '测地线' }]} />
        </Form.Item>
        {windowMode ? <Form.Item name={['windowOptions', 'observationCount']} label={<span>历史窗口<ContextHelp
          ariaLabel="运动历史窗口说明" content="包含当前观测。N 个点最多含 N−1 段；窗口不改变瞬时指标和静止分类，只有完整位于窗口内的段进入汇总。" /></span>} rules={[{ required: true }]}>
          <InputNumber aria-label="历史窗口观测数" min={1} max={100} precision={0} style={{ width: '100%' }} />
        </Form.Item> : <Form.Item name="historyPoints" rules={[{ required: true }]}
          label={<span className="canvas-inspector-field-label">历史点数<ContextHelp
            ariaLabel="运动统计历史点数说明" content="当前点会与前 N 个历史点比较；片段开头不足 N 个点时指标为 NULL。" /></span>}>
          <InputNumber min={1} max={100} precision={0} style={{ width: '100%' }} />
        </Form.Item>}
      </div>
      {windowMode && <Form.Item name={['windowOptions', 'orderByColumns']} label="同时间顺序">
        <Select mode="multiple" showSearch optionFilterProp="label" options={multipleColumnOptions(columns, windowOptions?.orderByColumns ?? [])} />
      </Form.Item>}
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>片段边界</Typography.Text>
          <Tag>{boundaryCount === 0 ? '不限' : `${boundaryCount} 项`}</Tag></Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setBoundariesOpen(true)}>设置</Button>
      </div>
      <Modal open={boundariesOpen} width={680} title="设置运动统计片段边界" okText="完成"
        cancelText="关闭" onOk={() => setBoundariesOpen(false)} onCancel={() => setBoundariesOpen(false)}>
        <TrackBoundaryEditor value={boundaries} onChange={(value) => updateField('boundaries', value)} />
      </Modal>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>输出指标</Typography.Text><Tag>{windowMode ? windowOptions?.statistics.length ?? 0 : metrics.length} 项</Tag></Space>
        <Button aria-label="设置运动指标" size="small" icon={<SettingOutlined />} onClick={() => setMetricsOpen(true)}>设置</Button>
      </div>
      <Modal open={metricsOpen} width={900} title="设置运动指标" okText="完成" cancelText="关闭"
        onOk={() => setMetricsOpen(false)} onCancel={() => setMetricsOpen(false)}>
        {windowMode ? (windowOptions && <MotionWindowEditor value={windowOptions} columns={columns}
          onChange={value => updateField('windowOptions', value)} />) : <>
        <div className="canvas-spatial-modal-toolbar">
          <Typography.Text type="secondary">指标按配置顺序追加，不展示任何实际轨迹值。</Typography.Text>
          <Button size="small" type="primary" icon={<PlusOutlined />} disabled={metrics.length >= 16}
            onClick={() => updateField('metrics', [...metrics, createMetric('DISTANCE')])}>添加指标</Button>
        </div>
        <Space orientation="vertical" size={6} style={{ width: '100%' }}>
          {metrics.map((metric, index) => <div className="canvas-track-metric-row" key={metric.metricId}>
            <Select value={metric.kind} options={metricKindOptions}
              onChange={(kind) => updateMetric(index, changeMetricKind(metric, kind))} />
            <Input value={metric.outputColumnName} placeholder="输出字段"
              onChange={(event) => updateMetric(index, { ...metric, outputColumnName: event.target.value })} />
            {metric.kind === 'IDLE'
              ? <Typography.Text type="secondary">使用下方全局静止阈值</Typography.Text>
              : <Select value={metric.outputUnit} options={unitOptions(metric)}
                onChange={(outputUnit) => updateMetric(index, { ...metric, outputUnit } as TrackMotionMetric)} />}
            <Space size={0}>
              <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                aria-label={`上移指标 ${index + 1}`} onClick={() => moveMetric(index, index - 1)} />
              <Button type="text" size="small" icon={<DownOutlined />} disabled={index === metrics.length - 1}
                aria-label={`下移指标 ${index + 1}`} onClick={() => moveMetric(index, index + 1)} />
              <Button type="text" danger size="small" icon={<DeleteOutlined />}
                aria-label={`删除指标 ${index + 1}`}
                onClick={() => updateField('metrics', metrics.filter((_, itemIndex) => itemIndex !== index))} />
            </Space>
          </div>)}
          {metrics.length === 0 && <Typography.Text type="secondary">尚未配置运动指标。</Typography.Text>}
        </Space>
        </>}
      </Modal>
      {hasIdle && <Form.Item label="静止距离阈值" required>
        <Space.Compact block>
          <Form.Item name="idleDistanceThreshold" noStyle rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '55%' }} />
          </Form.Item>
          <Form.Item name="idleDistanceThresholdUnit" noStyle rules={[{ required: true }]}>
            <Select options={trackDistanceUnitOptions} style={{ width: '45%' }} />
          </Form.Item>
        </Space.Compact>
      </Form.Item>}
      {windowMode && hasIdle && <Form.Item label="静止时间阈值" required>
        <Space.Compact block>
          <Form.Item name={['windowOptions', 'idleTimeThreshold']} noStyle rules={[{ required: true }]}>
            <InputNumber aria-label="静止时间阈值" min={0} style={{ width: '55%' }} />
          </Form.Item>
          <Form.Item name={['windowOptions', 'idleTimeThresholdUnit']} noStyle rules={[{ required: true }]}>
            <Select aria-label="静止时间阈值单位" options={trackDurationUnitOptions} style={{ width: '45%' }} />
          </Form.Item>
        </Space.Compact>
      </Form.Item>}
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
        <Input />
      </Form.Item>
    </Form>
  </Space>;
};

export default TrackMotionStatisticsInspector;
