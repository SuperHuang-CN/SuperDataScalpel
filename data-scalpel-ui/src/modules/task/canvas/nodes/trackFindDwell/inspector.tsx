import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { SettingOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type TrackFindDwellConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import {
  TrackBoundaryEditor,
  TrackSummaryEditor,
} from '../trackShared';
import {
  trackDistanceUnitOptions,
  trackDurationUnitOptions,
} from '../trackOptions';
import { createDwellRangeOptions, dwellFeatureMode, dwellResultOptions } from './rangeOptions';

const fingerprint = (value: TrackFindDwellConfiguration) => JSON.stringify(value);
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

const TrackFindDwellInspector = ({
  node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackFindDwell>) => {
  const [form] = Form.useForm<TrackFindDwellConfiguration>();
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const [summariesOpen, setSummariesOpen] = useState(false);
  const [summariesDraft, setSummariesDraft] = useState<TrackFindDwellConfiguration['summaryStatistics']>([]);
  const [fieldsOpen, setFieldsOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const timeColumnName = Form.useWatch('timeColumnName', form) ?? '';
  const trackIdColumns = Form.useWatch('trackIdColumns', form) ?? [];
  const boundaries = Form.useWatch('boundaries', { form, preserve: true }) ?? node.configuration.boundaries;
  const summaries = Form.useWatch('summaryStatistics', { form, preserve: true }) ?? [];
  const semantics = Form.useWatch('dwellSemantics', { form, preserve: true }) ?? node.configuration.dwellSemantics ?? 'LEGACY_ADJACENT';
  const range = Form.useWatch('rangeOptions', { form, preserve: true }) ?? node.configuration.rangeOptions;
  const referenceCenter = semantics === 'REFERENCE_CENTER';
  const features = referenceCenter && dwellFeatureMode(range?.resultMode);
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];

  const normalize = (value: TrackFindDwellConfiguration): TrackFindDwellConfiguration => ({
    dwellSemantics: value.dwellSemantics ?? 'LEGACY_ADJACENT',
    rangeOptions: value.rangeOptions ?? null,
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    trackIdColumns: value.trackIdColumns ?? [],
    timeColumnName: value.timeColumnName ?? '',
    distanceMethod: value.distanceMethod ?? null,
    distanceThreshold: value.distanceThreshold ?? 0,
    distanceThresholdUnit: value.distanceThresholdUnit ?? 'METERS',
    minimumDuration: value.minimumDuration ?? 0,
    minimumDurationUnit: value.minimumDurationUnit ?? 'MINUTES',
    boundaries: value.boundaries ?? node.configuration.boundaries,
    summaryStatistics: value.summaryStatistics ?? [],
    outputGeometryKind: value.outputGeometryKind ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    dwellIdColumnName: value.dwellIdColumnName?.trim() ?? '',
    startTimeColumnName: value.startTimeColumnName?.trim() ?? '',
    endTimeColumnName: value.endTimeColumnName?.trim() ?? '',
    durationColumnName: value.durationColumnName?.trim() ?? '',
    pointCountColumnName: value.pointCountColumnName?.trim() ?? '',
    outputGeometryColumnName: value.outputGeometryColumnName?.trim() ?? '',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: TrackFindDwellConfiguration) => {
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
  const updateField = <K extends keyof TrackFindDwellConfiguration>(name: K, value: TrackFindDwellConfiguration[K]) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };
  const boundaryCount = [boundaries.maximumTimeGap, boundaries.maximumDistanceGap, boundaries.fixedTimeBoundary]
    .filter((value) => value != null).length;

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<TrackFindDwellConfiguration> form={form} layout="vertical" autoComplete="off"
      initialValues={normalize(node.configuration)} onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}>
      <Form.Item label="识别语义">
        <Select aria-label="识别语义" value={semantics}
          options={[{ value: 'REFERENCE_CENTER', label: '参考点与均值中心' }, { value: 'LEGACY_ADJACENT', label: '相邻连段（旧版）' }]}
          onChange={(value: 'REFERENCE_CENTER' | 'LEGACY_ADJACENT') => {
            if (value === semantics) return;
            Modal.confirm({ title: '切换驻留识别语义？',
              content: '两种算法的驻留归属可能不同。已有输出和规则配置将保留，不自动换算距离或时长。',
              okText: '确认切换', cancelText: '取消', onOk: () => {
                if (value === 'REFERENCE_CENTER' && !form.getFieldValue('rangeOptions')) {
                  form.setFieldValue('rangeOptions', createDwellRangeOptions());
                }
                updateField('dwellSemantics', value);
              } });
          }} />
      </Form.Item>
      {!referenceCenter && <Typography.Text type="warning">旧版可能将缓慢移动连成驻留片段<ContextHelp
        ariaLabel="旧版驻留差异" content="旧版只限制相邻点步长；参考点与均值中心策略限制候选范围。切换会改变结果，旧任务不会自动切换。" /></Typography.Text>}
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
      {referenceCenter && <Form.Item name={['rangeOptions', 'orderByColumns']} label={<span>同时间顺序<ContextHelp
        ariaLabel="驻留同时间顺序" content="按时间及这些字段升序处理。同轨迹仍有相同次序的观测时运行报错，不随机选择；NULL 次序值在前。" /></span>}>
        <Select mode="multiple" showSearch optionFilterProp="label"
          options={multipleColumnOptions(columns, range?.orderByColumns ?? [])} />
      </Form.Item>}
      <Form.Item name="distanceMethod" label="距离方法" rules={[{ required: true }]}>
        <Segmented block options={[{ value: 'PLANAR', label: '平面' }, { value: 'GEODESIC', label: '测地线' }]} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item label={<span className="canvas-inspector-field-label">驻留距离容差<ContextHelp
          ariaLabel="驻留距离容差说明" content="先以候选首点收集连续范围，满足时长后计算均值中心；以固定中心向前后扩展连续观测，不复用已归属点。不是相邻点步长上限。" /></span>} required>
          <Space.Compact block>
            <Form.Item name="distanceThreshold" noStyle rules={[{ required: true }]}>
              <InputNumber min={Number.MIN_VALUE} style={{ width: '55%' }} />
            </Form.Item>
            <Form.Item name="distanceThresholdUnit" noStyle rules={[{ required: true }]}>
              <Select options={trackDistanceUnitOptions} style={{ width: '45%' }} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>
        <Form.Item label="最短持续时间" required>
          <Space.Compact block>
            <Form.Item name="minimumDuration" noStyle rules={[{ required: true }]}>
              <InputNumber min={Number.MIN_VALUE} style={{ width: '55%' }} />
            </Form.Item>
            <Form.Item name="minimumDurationUnit" noStyle rules={[{ required: true }]}>
              <Select options={trackDurationUnitOptions} style={{ width: '45%' }} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>
      </div>
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>轨迹边界</Typography.Text>
          <Tag>{boundaryCount === 0 ? '不限' : `${boundaryCount} 项`}</Tag></Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setBoundariesOpen(true)}>设置</Button>
      </div>
      <Modal open={boundariesOpen} width={680} title="设置驻留分析轨迹边界" okText="完成"
        cancelText="关闭" onOk={() => setBoundariesOpen(false)} onCancel={() => setBoundariesOpen(false)}>
        <TrackBoundaryEditor value={boundaries} onChange={(value) => updateField('boundaries', value)} />
      </Modal>
      {referenceCenter ? <Form.Item name={['rangeOptions', 'resultMode']} label="输出类型" rules={[{ required: true }]}>
        <Select options={dwellResultOptions} />
      </Form.Item> : <Form.Item name="outputGeometryKind" label="驻留位置" rules={[{ required: true }]}>
        <Segmented block options={[{ value: 'CENTROID', label: '中心点' }, { value: 'CONVEX_HULL', label: '凸包' }]} />
      </Form.Item>}
      {!features && <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>片段汇总</Typography.Text><Tag>{summaries.length} 项</Tag></Space>
        <Button aria-label="设置驻留片段汇总" size="small" icon={<SettingOutlined />} onClick={() => {
          const current: TrackFindDwellConfiguration = form.getFieldsValue(true);
          setSummariesDraft((current.summaryStatistics ?? []).map(item => ({ ...item })));
          setSummariesOpen(true);
        }}>设置</Button>
      </div>}
      <Modal open={summariesOpen} destroyOnHidden width={860} title="设置驻留片段汇总" okText="保存汇总草稿"
        cancelText="取消" onOk={() => { updateField('summaryStatistics', summariesDraft); setSummariesOpen(false); }} onCancel={() => setSummariesOpen(false)}>
        <TrackSummaryEditor value={summariesDraft} columns={columns} allowNumericAny validationAvailable={validation != null}
          onChange={setSummariesDraft} />
      </Modal>
      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>结果字段</Typography.Text><Tag>{features ? 2 : referenceCenter ? 7 : 6} 个</Tag></Space>
        <Button aria-label="设置驻留结果字段" size="small" icon={<SettingOutlined />} onClick={() => setFieldsOpen(true)}>设置</Button>
      </div>
      <Modal open={fieldsOpen} width={680} title="设置驻留结果字段" okText="完成" cancelText="关闭"
        onOk={() => setFieldsOpen(false)} onCancel={() => setFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="dwellIdColumnName" label="驻留 ID" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          {features ? <Form.Item name={['rangeOptions', 'dwellFlagColumnName']} label="驻留标记" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item> : <>
          <Form.Item name="outputGeometryColumnName" label="驻留 Geometry" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="startTimeColumnName" label="开始时间" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="endTimeColumnName" label="结束时间" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="durationColumnName" label="持续时间" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="pointCountColumnName" label="点数" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          {referenceCenter && <>
            <Form.Item name={['rangeOptions', 'durationUnit']} label="输出时长单位" rules={[{ required: true }]}><Select options={trackDurationUnitOptions} /></Form.Item>
            <Form.Item name={['rangeOptions', 'meanDistanceColumnName']} label="平均相邻距离字段" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
            <Form.Item name={['rangeOptions', 'meanDistanceUnit']} label="输出距离单位" rules={[{ required: true }]}><Select options={trackDistanceUnitOptions} /></Form.Item>
          </>}
          </>}
        </div>
      </Modal>
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
    </Form>
  </Space>;
};

export default TrackFindDwellInspector;
