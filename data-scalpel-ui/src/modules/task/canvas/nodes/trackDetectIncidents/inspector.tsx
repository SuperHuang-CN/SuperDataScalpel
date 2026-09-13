import { ContextHelp, InlineFeedback } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, EditOutlined, SettingOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Segmented, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasFilterCondition,
  type TrackDetectIncidentsConfiguration,
  type TrackIncidentSemantics,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { FilterConditionTreeEditor } from '../../components/processors/FilterProcessorInspector';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { TrackBoundaryEditor } from '../trackShared';
import { trackDurationUnitOptions } from '../trackOptions';
import { incidentConditionColumns, incidentScalarErrors, incidentWindowErrors,
  isIncidentPointCoordinateScalar } from './conditionWindows';
import { IncidentScalarsModal } from './IncidentScalarsModal';
import { IncidentWindowsModal } from './IncidentWindowsModal';

const emptyCondition = (): CanvasFilterCondition => ({ kind: 'GROUP', operator: 'AND', children: [] });
const conditionCount = (condition: CanvasFilterCondition | null): number => {
  if (!condition) return 0;
  return condition.kind === 'PREDICATE'
    ? 1
    : condition.children.reduce((sum, child) => sum + conditionCount(child), 0);
};
const fingerprint = (value: TrackDetectIncidentsConfiguration) => JSON.stringify(value);
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

const TrackDetectIncidentsInspector = ({
  node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackDetectIncidents>) => {
  const [form] = Form.useForm<TrackDetectIncidentsConfiguration>();
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const [startOpen, setStartOpen] = useState(false);
  const [endOpen, setEndOpen] = useState(false);
  const [startDraft, setStartDraft] = useState<CanvasFilterCondition>(emptyCondition);
  const [endDraft, setEndDraft] = useState<CanvasFilterCondition>(emptyCondition);
  const [fieldsOpen, setFieldsOpen] = useState(false);
  const [windowsOpen, setWindowsOpen] = useState(false);
  const [scalarsOpen, setScalarsOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? null;
  const timeColumnName = Form.useWatch('timeColumnName', form) ?? '';
  const trackIdColumns = Form.useWatch('trackIdColumns', form) ?? [];
  const incidentSemantics = Form.useWatch('incidentSemantics', { form, preserve: true }) ?? node.configuration.incidentSemantics ?? 'LEGACY';
  const orderByColumns = Form.useWatch('orderByColumns', form) ?? [];
  const lifecycle = incidentSemantics === 'CONDITION_LIFECYCLE';
  const boundaries = Form.useWatch('boundaries', { form, preserve: true }) ?? node.configuration.boundaries;
  const startCondition = Form.useWatch('startCondition', { form, preserve: true }) ?? emptyCondition();
  const endCondition = Form.useWatch('endCondition', { form, preserve: true }) ?? null;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const conditionWindows = Form.useWatch('conditionWindows', { form, preserve: true }) ?? node.configuration.conditionWindows ?? [];
  const conditionScalars = Form.useWatch('conditionScalars', { form, preserve: true }) ?? node.configuration.conditionScalars ?? [];
  const conditionColumns = lifecycle ? incidentConditionColumns(columns, conditionWindows, conditionScalars) : columns;
  const motionWindowCount = conditionWindows.filter(window => (window.source ?? 'FIELD') !== 'FIELD').length;
  const pointCoordinateScalarCount = conditionScalars.filter(isIncidentPointCoordinateScalar).length;
  const windowErrors = lifecycle
    ? incidentWindowErrors(conditionWindows, columns, Boolean(validation), pointGeometryColumnName, conditionScalars) : [];
  const windowErrorDetails = windowErrors.flatMap((row, index) => Object.values(row).map(message => `第 ${index + 1} 项：${message}`));
  const scalarErrors = lifecycle ? incidentScalarErrors(conditionScalars, columns, conditionWindows,
    Boolean(validation), pointGeometryColumnName) : [];
  const scalarErrorDetails = scalarErrors.flatMap((row, index) => Object.values(row).map(message => `第 ${index + 1} 项：${message}`));

  const normalize = (value: TrackDetectIncidentsConfiguration): TrackDetectIncidentsConfiguration => ({
    conditionWindows: value.conditionWindows ?? [],
    conditionScalars: value.conditionScalars ?? [],
    incidentSemantics: value.incidentSemantics ?? 'LEGACY',
    incidentStatusColumnName: value.incidentStatusColumnName ?? null,
    orderByColumns: value.orderByColumns ?? [],
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName || null,
    trackIdColumns: value.trackIdColumns ?? [],
    timeColumnName: value.timeColumnName ?? '',
    distanceMethod: value.distanceMethod ?? null,
    boundaries: value.boundaries ?? node.configuration.boundaries,
    startCondition: value.startCondition ?? emptyCondition(),
    endCondition: value.endCondition ?? null,
    resultMode: value.resultMode ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    incidentIdColumnName: value.incidentIdColumnName?.trim() ?? '',
    incidentFlagColumnName: value.incidentFlagColumnName?.trim() ?? '',
    incidentStartTimeColumnName: value.incidentStartTimeColumnName?.trim() ?? '',
    incidentEndTimeColumnName: value.incidentEndTimeColumnName?.trim() ?? '',
    incidentDurationColumnName: value.incidentDurationColumnName?.trim() ?? '',
    incidentDurationUnit: value.incidentDurationUnit ?? 'MINUTES',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: TrackDetectIncidentsConfiguration) => {
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
  const updateField = <K extends keyof TrackDetectIncidentsConfiguration>(
    name: K,
    value: TrackDetectIncidentsConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };
  const boundaryCount = [boundaries.maximumTimeGap, boundaries.maximumDistanceGap, boundaries.fixedTimeBoundary]
    .filter((value) => value != null).length;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<TrackDetectIncidentsConfiguration> form={form} layout="vertical" autoComplete="off"
      initialValues={normalize(node.configuration)} onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}>
      <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" disabled={!validation}
          options={spatialTableOptions(tables, sourceTableName)} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="timeColumnName" label="时间字段" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns, timeColumnName, (column) => column.fieldType === 'TIMESTAMP',
          )} />
        </Form.Item>
        <Form.Item name="pointGeometryColumnName" label="Geometry（可选）">
          <Select allowClear showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns, pointGeometryColumnName ?? '',
            (column) => {
              if (column.fieldType !== 'GEOMETRY') return false;
              if (motionWindowCount > 0) return column.geometry?.kind === 'POINT'
                && column.geometry.dimension === 'XY'
                && column.geometry.crs.authority.toUpperCase() === 'EPSG'
                && column.geometry.crs.code === 4326;
              if (pointCoordinateScalarCount > 0 || !lifecycle || boundaries.maximumDistanceGap != null) {
                return column.geometry?.kind === 'POINT';
              }
              return true;
            },
          )} />
        </Form.Item>
      </div>
      <Form.Item name="trackIdColumns" label="轨迹标识" rules={[{ required: true, type: 'array', min: 1 }]}>
        <Select mode="multiple" maxCount={8} showSearch optionFilterProp="label"
          options={multipleColumnOptions(columns, trackIdColumns)} />
      </Form.Item>
      <Form.Item label={<span className="canvas-inspector-field-label">事件语义<ContextHelp ariaLabel="事件语义说明"
        content="条件生命周期：开始条件触发一次事件，结束观测不计入成员，输出 Started/OnGoing/Ended 与逐观测持续时间。旧版行为仅用于重现已有任务，切换会改变结果。" /></span>}>
        <Select<TrackIncidentSemantics> aria-label="事件语义" value={incidentSemantics} options={[
          { value: 'CONDITION_LIFECYCLE', label: '条件生命周期' },
          { value: 'LEGACY', label: '旧版行为（兼容）' },
        ]} onChange={(value) => Modal.confirm({
          title: '切换事件语义？', content: '这会改变事件成员、结束边界和持续时间的计算。已有条件和字段配置会保留。',
          okText: '确认切换', cancelText: '取消', onOk: () => {
            form.setFieldsValue({ incidentSemantics: value,
              incidentStatusColumnName: form.getFieldValue('incidentStatusColumnName') || 'incident_status' });
            markDirty();
          },
        })} />
      </Form.Item>
      {lifecycle && <Form.Item name="orderByColumns" label={<span className="canvas-inspector-field-label">
        同时间顺序<ContextHelp ariaLabel="同时间顺序说明"
          content="先按时间升序，再按所选字段顺序排序。相同轨迹中仍无法区分的同时间观测会在运行时报错；不随机选择次序。没有时间的观测不参与检测。" /></span>}>
        <Select mode="multiple" showSearch optionFilterProp="label" options={multipleColumnOptions(columns, orderByColumns)} />
      </Form.Item>}
      {pointGeometryColumnName && (!lifecycle || boundaries.maximumDistanceGap != null)
        && <Form.Item name="distanceMethod" label="空间距离方法" rules={[{ required: true }]}>
        <Segmented block options={[{ value: 'PLANAR', label: '平面' }, { value: 'GEODESIC', label: '测地线' }]} />
      </Form.Item>}
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>轨迹边界</Typography.Text>
          <Tag>{boundaryCount === 0 ? '不限' : `${boundaryCount} 项`}</Tag></Space>
        <Button size="small" aria-label="设置轨迹边界" icon={<SettingOutlined />} onClick={() => setBoundariesOpen(true)}>设置</Button>
      </div>
      <Modal open={boundariesOpen} width={680} title="设置事件检测轨迹边界" okText="完成"
        cancelText="关闭" onOk={() => setBoundariesOpen(false)} onCancel={() => setBoundariesOpen(false)}>
        <TrackBoundaryEditor value={boundaries} onChange={(value) => updateField('boundaries', value)} />
      </Modal>

      <div className="canvas-track-condition-row">
        <Space wrap size={6} style={{ minWidth: 0 }}><Typography.Text strong>窗口指标</Typography.Text><Tag>{conditionWindows.length} 项{!lifecycle && conditionWindows.length > 0 ? '（未启用）' : ''}</Tag>
          {windowErrorDetails.length > 0 && <InlineFeedback tone="warning" label={`${windowErrorDetails.length} 个问题`}
            detail={windowErrorDetails.map((message, index) => <div key={index}>{message}</div>)} />}
          <ContextHelp ariaLabel="窗口指标使用说明" content="配置字段、WGS84 累计轨迹距离、逐观测速度或加速度窗口后，可在开始和结束条件中选择指标名。运动单位固定为米、米/秒和米/秒²；仅条件生命周期模式执行，切回旧模式保留但不计算这些配置。" /></Space>
        <Button size="small" aria-label="配置事件窗口指标" disabled={!lifecycle} icon={<SettingOutlined />} onClick={() => setWindowsOpen(true)}>设置</Button>
      </div>
      {windowsOpen && <IncidentWindowsModal value={conditionWindows} columns={columns} scalars={conditionScalars}
        pointGeometryColumnName={pointGeometryColumnName} schemaAvailable={Boolean(validation)} onCancel={() => setWindowsOpen(false)}
        onSave={value => { updateField('conditionWindows', value); setWindowsOpen(false); }} />}
      <div className="canvas-track-condition-row">
        <Space wrap size={6} style={{ minWidth: 0 }}><Typography.Text strong>轨迹标量</Typography.Text>
          <Tag>{conditionScalars.length} 项{!lifecycle && conditionScalars.length > 0 ? '（未启用）' : ''}</Tag>
          {scalarErrorDetails.length > 0 && <InlineFeedback tone="warning" label={`${scalarErrorDetails.length} 个问题`}
            detail={scalarErrorDetails.map((message, index) => <div key={index}>{message}</div>)} />}
          <ContextHelp ariaLabel="轨迹标量使用说明"
            content="把轨迹时间、时长、序号或相对观测的 Point X/Y 坐标绑定为条件字段。时间使用 Epoch 毫秒，序号从 0 开始；坐标偏移 0 表示当前、负数回看、正数前看，坐标单位跟随来源 CRS。轨迹边界会重置这些值。" />
        </Space>
        <Button size="small" aria-label="配置轨迹条件标量" disabled={!lifecycle}
          icon={<SettingOutlined />} onClick={() => setScalarsOpen(true)}>设置</Button>
      </div>
      {scalarsOpen && <IncidentScalarsModal value={conditionScalars} columns={columns} windows={conditionWindows}
        pointGeometryColumnName={pointGeometryColumnName} schemaAvailable={Boolean(validation)}
        onCancel={() => setScalarsOpen(false)}
        onSave={value => { updateField('conditionScalars', value); setScalarsOpen(false); }} />}
      <div className="canvas-track-condition-row">
        <Space size={6}><Typography.Text strong>开始条件</Typography.Text><Tag>{conditionCount(startCondition)} 个条件</Tag></Space>
        <Button size="small" icon={<EditOutlined />} aria-label="编辑事件开始条件" onClick={() => {
          setStartDraft(structuredClone(startCondition)); setStartOpen(true);
        }}>编辑</Button>
      </div>
      <Modal open={startOpen} width={860} title="编辑事件开始条件" okText="保存条件草稿" cancelText="取消"
        onOk={() => { updateField('startCondition', startDraft); setStartOpen(false); }} onCancel={() => setStartOpen(false)}>
        <FilterConditionTreeEditor condition={startDraft} columns={conditionColumns} onChange={setStartDraft} />
      </Modal>
      <div className="canvas-track-condition-row">
        <Space size={6}><Typography.Text strong>结束条件</Typography.Text>
          <ContextHelp ariaLabel="事件结束条件说明" content={lifecycle
            ? '未配置时，开始条件变为不成立即结束。已配置时，结束条件成立优先关闭事件；结束观测标记 Ended，但不属于事件成员。'
            : '旧版行为：开始后保持激活至片段结束，显式结束命中行仍计入事件。可切换条件生命周期语义。'} />
          <Tag>{endCondition ? `${conditionCount(endCondition)} 个条件` : '未配置'}</Tag></Space>
        <Space size={4}>
          {endCondition && <Button size="small" type="text" danger icon={<DeleteOutlined />}
            aria-label="清除事件结束条件" onClick={() => updateField('endCondition', null)} />}
          <Button size="small" icon={<EditOutlined />} aria-label="编辑事件结束条件" onClick={() => {
            setEndDraft(structuredClone(endCondition ?? emptyCondition()));
            setEndOpen(true);
          }}>{endCondition ? '编辑' : '配置'}</Button>
        </Space>
      </div>
      <Modal open={endOpen} width={860} title="编辑事件结束条件" okText="保存条件草稿" cancelText="取消"
        onOk={() => { updateField('endCondition', endDraft); setEndOpen(false); }} onCancel={() => setEndOpen(false)}>
        <FilterConditionTreeEditor condition={endDraft} columns={conditionColumns} onChange={setEndDraft} />
      </Modal>
      <Form.Item name="resultMode" rules={[{ required: true }]}
        label={<span className="canvas-inspector-field-label">结果范围<ContextHelp ariaLabel="事件结果范围说明"
          content="仅事件会过滤掉事件外行；全部并标记会保留来源行并追加事件状态字段。" /></span>}>
        <Segmented block options={[{ value: 'INCIDENTS_ONLY', label: '仅事件' }, { value: 'ALL_EVENTS', label: '全部并标记' }]} />
      </Form.Item>
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>事件结果字段</Typography.Text><Tag>{lifecycle ? 6 : 5} 个</Tag></Space>
        <Button size="small" aria-label="设置事件结果字段" icon={<SettingOutlined />} onClick={() => setFieldsOpen(true)}>设置</Button>
      </div>
      <Modal open={fieldsOpen} width={680} title="设置事件结果字段" okText="完成" cancelText="关闭"
        onOk={() => setFieldsOpen(false)} onCancel={() => setFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="incidentIdColumnName" label="事件 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="incidentFlagColumnName" label="事件标记" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          {lifecycle && <Form.Item name="incidentStatusColumnName" label="生命周期状态" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>}
          <Form.Item name="incidentStartTimeColumnName" label="开始时间" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="incidentEndTimeColumnName" label="结束时间" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="incidentDurationColumnName" label={lifecycle ? '当前观测已持续时间' : '整段持续时间'} rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="incidentDurationUnit" label="持续时间单位" rules={[{ required: true }]}><Select options={trackDurationUnitOptions} /></Form.Item>
        </div>
      </Modal>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
    </Form>
  </Space>;
};

export default TrackDetectIncidentsInspector;
