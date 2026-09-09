import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { SettingOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type TrackReconstructConfiguration,
  type TrackReconstructOptions,
  type TrackSplitExpression,
  type TrackPathGeometryOptions,
  type TrackAreaGeometryOptions,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { TrackBoundaryEditor, TrackSummaryEditor } from '../trackShared';
import { boundaryLabels, createPathGeometryOptions, createReconstructionOptions, usesMethodPath, usesOrderedReconstruction, usesSplitExpression } from './reconstruction';
import { trackDistanceUnitOptions } from '../trackOptions';
import { TrackSplitEditor } from './splitEditor';
import { areaBufferLabel, areaGeometryProblems, createAreaGeometryOptions, usesAreaGeometry } from './areaGeometry';
import { AreaGeometryEditor } from './AreaGeometryEditor';

const fingerprint = (value: TrackReconstructConfiguration) => JSON.stringify(value);

const multipleColumnOptions = (columns: CanvasColumnSchema[], selected: string[]) => {
  const names = new Set(columns.map((column) => column.name));
  return [
    ...selected.filter((name) => !names.has(name)).map((name) => ({
      value: name,
      label: `${name}（已失效）`,
      disabled: true,
    })),
    ...columns.filter((column) => column.fieldType !== 'GEOMETRY').map((column) => ({
      value: column.name,
      label: `${column.name} · ${column.fieldType}`,
    })),
  ];
};

const TrackReconstructInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TrackReconstruct>) => {
  const [form] = Form.useForm<TrackReconstructConfiguration>();
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const [summariesOpen, setSummariesOpen] = useState(false);
  const [summariesDraft, setSummariesDraft] = useState<TrackReconstructConfiguration['summaryStatistics']>([]);
  const [splitOpen, setSplitOpen] = useState(false);
  const [splitDraft, setSplitDraft] = useState<TrackSplitExpression>({ expression: '', bindings: [] });
  const [areaOpen, setAreaOpen] = useState(false);
  const [areaDraft, setAreaDraft] = useState<TrackAreaGeometryOptions>(() => createAreaGeometryOptions(false));
  const reconstruction: TrackReconstructOptions | null | undefined = Form.useWatch('reconstruction', { form, preserve: true }) ?? node.configuration.reconstruction;
  const ordered = usesOrderedReconstruction(reconstruction);
  const methodPath = usesMethodPath(reconstruction);
  const area = usesAreaGeometry(reconstruction);
  const distanceMethod = Form.useWatch('distanceMethod', form) ?? node.configuration.distanceMethod;
  const pathGeometry = reconstruction?.pathGeometry;
  const invalidLength = pathGeometry?.maximumGeodesicSegmentLength == null || pathGeometry.maximumGeodesicSegmentLength <= 0;
  const invalidUnit = pathGeometry?.maximumGeodesicSegmentLengthUnit == null || pathGeometry.maximumGeodesicSegmentLengthUnit === 'SOURCE_CRS_UNIT';
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const timeColumnName = Form.useWatch('timeColumnName', form) ?? '';
  const trackIdColumns = Form.useWatch('trackIdColumns', form) ?? [];
  const boundaries = Form.useWatch('boundaries', { form, preserve: true }) ?? node.configuration.boundaries;
  const summaries = Form.useWatch('summaryStatistics', { form, preserve: true }) ?? [];
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const sourceGeometry = columns.find(column => column.name === pointGeometryColumnName);
  const polygon = sourceGeometry?.geometry?.kind === 'POLYGON' || sourceGeometry?.geometry?.kind === 'MULTIPOLYGON';
  const areaProblems = area && reconstruction?.areaGeometry
    ? areaGeometryProblems(reconstruction.areaGeometry, sourceGeometry, columns, validation != null, distanceMethod) : [];

  const normalize = (value: TrackReconstructConfiguration): TrackReconstructConfiguration => ({
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    trackIdColumns: value.trackIdColumns ?? [],
    timeColumnName: value.timeColumnName ?? '',
    distanceMethod: value.distanceMethod ?? null,
    boundaries: value.boundaries ?? node.configuration.boundaries,
    summaryStatistics: value.summaryStatistics ?? [],
    outputTableName: value.outputTableName?.trim() ?? '',
    outputGeometryColumnName: value.outputGeometryColumnName?.trim() ?? '',
    startTimeColumnName: value.startTimeColumnName?.trim() ?? '',
    endTimeColumnName: value.endTimeColumnName?.trim() ?? '',
    pointCountColumnName: value.pointCountColumnName?.trim() ?? '',
    ...('reconstruction' in value ? { reconstruction: value.reconstruction } : {}),
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: TrackReconstructConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(value) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      },
    }),
  );

  const updateBoundaries = (value: TrackReconstructConfiguration['boundaries']) => {
    form.setFieldValue('boundaries', value);
    markDirty({ ...form.getFieldsValue(true), boundaries: value });
  };
  const updateSummaries = (value: TrackReconstructConfiguration['summaryStatistics']) => {
    form.setFieldValue('summaryStatistics', value);
    markDirty({ ...form.getFieldsValue(true), summaryStatistics: value });
  };
  const updateReconstruction = (patch: Partial<TrackReconstructOptions>) => {
    form.setFieldValue('reconstruction', { ...(reconstruction ?? createReconstructionOptions()), ...patch });
    markDirty();
  };
  const updatePath = (patch: Partial<TrackPathGeometryOptions>) => updateReconstruction({
    pathGeometry: { ...(pathGeometry ?? createPathGeometryOptions()), ...patch },
  });
  const boundaryCount = [boundaries.maximumTimeGap, boundaries.maximumDistanceGap, boundaries.fixedTimeBoundary]
    .filter((value) => value != null).length;

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<TrackReconstructConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => markDirty()}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)} />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="pointGeometryColumnName" label="观测 Geometry" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              columns, pointGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY' && (column.geometry?.kind === 'POINT'
                || area && (column.geometry?.kind === 'POLYGON' || column.geometry?.kind === 'MULTIPOLYGON')),
            )} />
          </Form.Item>
          <Form.Item name="timeColumnName" label="时间字段" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              columns, timeColumnName,
              (column) => column.fieldType === 'TIMESTAMP',
            )} />
          </Form.Item>
        </div>
        <Form.Item name="trackIdColumns" label="轨迹标识" rules={[{ required: true, type: 'array', min: 1 }]}>
          <Select mode="multiple" maxCount={8} showSearch optionFilterProp="label"
            options={multipleColumnOptions(columns, trackIdColumns)} />
        </Form.Item>
        <Form.Item name="distanceMethod" label="距离方法" rules={[{ required: true }]}>
          <Segmented block options={[{ value: 'PLANAR', label: '平面' }, { value: 'GEODESIC', label: '测地线' }]} />
        </Form.Item>

        <Form.Item label={<span className="canvas-inspector-field-label">重建策略<ContextHelp ariaLabel="轨迹重建策略说明"
          content="有序片段按时间及同时间字段排序，重复次序在真实执行时报错；跳过无时间、NULL/空几何与单点线片段。面片段保留单个观测。旧版保持原语义。" /></span>}>
          <Select<'ORDERED_SEGMENTS' | 'LEGACY_POINTS'> aria-label="轨迹重建策略" value={ordered ? 'ORDERED_SEGMENTS' : 'LEGACY_POINTS'}
            options={[{ value: 'ORDERED_SEGMENTS', label: '有序片段' }, { value: 'LEGACY_POINTS', label: '旧版点连线' }]}
            onChange={semantics => Modal.confirm({ title: '切换轨迹重建策略？',
              content: '次序、拆分和单点处理会发生变化。已有次序、表达式及连接段设置保留，请检查下游。', okText: '确认切换',
              onOk: () => updateReconstruction({ semantics }),
            })} />
        </Form.Item>
        {ordered && <>
          <Form.Item label="输出形态">
            <Select<'LINE' | 'AREA'> aria-label="轨迹输出形态" value={area ? 'AREA' : 'LINE'}
              options={[{ value: 'LINE', label: '线轨迹' }, { value: 'AREA', label: '面轨迹 · XY' }]}
              onChange={mode => Modal.confirm({ title: '切换轨迹输出形态？',
                content: 'Geometry 类型与单观测片段处理会变化，请检查下游。已有线轨迹设置、缓冲字段和表达式均保留。',
                okText: '确认切换', cancelText: '取消', onOk: () => updateReconstruction({ areaGeometry: {
                  ...(reconstruction?.areaGeometry ?? createAreaGeometryOptions(polygon)), enabled: mode === 'AREA',
                } }),
              })} />
          </Form.Item>
          {area && <div className="canvas-processor-section-header">
            <Space size={4}><Typography.Text strong>面轨迹设置</Typography.Text>
              <Tag>{areaBufferLabel(reconstruction?.areaGeometry?.bufferMode)}</Tag>
              {areaProblems.length > 0 && <><Typography.Text type="danger">{areaProblems.length} 个问题</Typography.Text>
                <ContextHelp ariaLabel="当前面轨迹配置问题" content={areaProblems.join('；')} /></>}
            </Space>
            <Button size="small" icon={<SettingOutlined />} aria-label="设置面轨迹" onClick={() => {
              setAreaDraft(structuredClone(reconstruction?.areaGeometry ?? createAreaGeometryOptions(polygon)));
              setAreaOpen(true);
            }}>设置</Button>
          </div>}
          {!area && <Form.Item label={<span className="canvas-inspector-field-label">路径几何<ContextHelp ariaLabel="轨迹路径几何说明"
            content="按距离方法生成 MultiLineString：平面保留原顶点；测地线使用 WGS84 椭球加密并在日期变更线切开。加密只改变 Geometry，不增加观测点数或统计样本。旧版仍输出未加密 LineString。" /></span>}>
            <Select<NonNullable<TrackPathGeometryOptions['mode']>> aria-label="轨迹路径几何" value={methodPath ? 'METHOD_PATH' : 'LEGACY_VERTEX_LINE'}
              options={[{ value: 'METHOD_PATH', label: '按距离方法 · 多部件线' }, { value: 'LEGACY_VERTEX_LINE', label: '旧版顶点连线' }]}
              onChange={mode => Modal.confirm({ title: '切换路径几何？', content: '输出几何类型和测地路径会变化，请检查下游。加密设置保留。',
                okText: '确认切换', onOk: () => updatePath({ mode }) })} />
          </Form.Item>}
          {!area && methodPath && distanceMethod === 'GEODESIC' && <Form.Item validateStatus={invalidLength || invalidUnit ? 'error' : undefined}
            help={invalidLength || invalidUnit ? '请输入正数及线性距离单位' : undefined}
            label={<span className="canvas-inspector-field-label">测地最大段长<ContextHelp ariaLabel="测地最大段长说明"
              content="相邻观测沿最短测地线等距离加密。10 千米是平台初值，不是 ArcGIS 默认值或误差承诺。单片段最多一百万顶点，超限明确失败；可增大段长或拆分轨迹。" /></span>}>
            <Space.Compact block>
              <InputNumber aria-label="测地最大段长" value={pathGeometry?.maximumGeodesicSegmentLength}
                onChange={maximumGeodesicSegmentLength => updatePath({ maximumGeodesicSegmentLength })} />
              <Select aria-label="测地段长单位" value={pathGeometry?.maximumGeodesicSegmentLengthUnit} allowClear
                options={trackDistanceUnitOptions.map(option => ({ ...option, disabled: option.value === 'SOURCE_CRS_UNIT' }))}
                onChange={unit => updatePath({ maximumGeodesicSegmentLengthUnit: unit ?? null })} />
            </Space.Compact>
          </Form.Item>}
          <Form.Item label="同时间顺序">
            <Select aria-label="轨迹同时间顺序" mode="multiple" showSearch optionFilterProp="label"
              value={reconstruction?.orderByColumns ?? []}
              options={multipleColumnOptions(columns, reconstruction?.orderByColumns ?? [])}
              onChange={orderByColumns => updateReconstruction({ orderByColumns })} />
          </Form.Item>
          <Form.Item label={<span className="canvas-inspector-field-label">拆分连接段<ContextHelp ariaLabel="拆分连接段归属说明"
            content="留空不生成跨拆分线段；归前段将后一点同时用于前段，归后段将前一点同时用于后段。共享端点参与两段起止时间、点数与统计。只作用于间隔/表达式拆分，固定周期始终留空。" /></span>}>
            <Select aria-label="拆分连接段归属" value={reconstruction?.splitBoundaryOption ?? 'GAP'}
              options={Object.entries(boundaryLabels).map(([value, label]) => ({ value, label }))}
              onChange={splitBoundaryOption => updateReconstruction({ splitBoundaryOption })} />
          </Form.Item>
          <div className="canvas-processor-section-header">
            <Space size={6}><Typography.Text strong>表达式拆分</Typography.Text><Tag>{usesSplitExpression(reconstruction) ? '已配置' : '关闭'}</Tag></Space>
            <Button size="small" icon={<SettingOutlined />} aria-label="设置轨迹拆分表达式" onClick={() => {
              setSplitDraft(structuredClone(reconstruction?.splitExpression ?? { expression: '', bindings: [] }));
              setSplitOpen(true);
            }}>设置</Button>
          </div>
        </>}
        <Modal open={areaOpen} destroyOnHidden width={680} title="设置面轨迹" okText="保存面轨迹草稿" cancelText="取消"
          onCancel={() => setAreaOpen(false)} onOk={() => { updateReconstruction({ areaGeometry: areaDraft }); setAreaOpen(false); }}>
          <AreaGeometryEditor value={areaDraft} columns={columns} geometry={sourceGeometry}
            validationAvailable={validation != null} distanceMethod={distanceMethod} onChange={setAreaDraft} />
        </Modal>
        <Modal open={splitOpen} width={860} title="设置轨迹拆分表达式" okText="保存草稿" cancelText="取消"
          onCancel={() => setSplitOpen(false)} onOk={() => { updateReconstruction({ splitExpression: splitDraft }); setSplitOpen(false); }}>
          <TrackSplitEditor value={splitDraft} columns={columns} onChange={setSplitDraft} />
        </Modal>

        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>轨迹拆分</Typography.Text>
            <Tag>{boundaryCount === 0 ? '不限' : `${boundaryCount} 项边界`}</Tag>
            <ContextHelp ariaLabel="轨迹拆分说明" content="相邻事件超过任一时间或空间阈值时开始新的轨迹片段。固定时间周期独立生效，始终留空，不添加跨周期连接段。" />
          </Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setBoundariesOpen(true)}>设置</Button>
        </div>
        <Modal open={boundariesOpen} width={680} title="设置轨迹拆分边界" okText="完成"
          cancelText="关闭" onOk={() => setBoundariesOpen(false)} onCancel={() => setBoundariesOpen(false)}>
          <TrackBoundaryEditor value={boundaries} onChange={updateBoundaries} />
        </Modal>

        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>片段汇总</Typography.Text><Tag>{summaries.length} 项</Tag></Space>
          <Button aria-label="设置轨迹片段汇总" size="small" icon={<SettingOutlined />} onClick={() => {
            const current: TrackReconstructConfiguration = form.getFieldsValue(true);
            setSummariesDraft((current.summaryStatistics ?? []).map(item => ({ ...item })));
            setSummariesOpen(true);
          }}>设置</Button>
        </div>
        <Modal open={summariesOpen} destroyOnHidden width={860} title="设置轨迹片段汇总" okText="保存汇总草稿"
          cancelText="取消" onOk={() => { updateSummaries(summariesDraft); setSummariesOpen(false); }} onCancel={() => setSummariesOpen(false)}>
          <TrackSummaryEditor value={summariesDraft} columns={columns} onChange={setSummariesDraft} validationAvailable={validation != null} />
        </Modal>

        <div className="canvas-spatial-pair-grid">
          <Form.Item name="outputGeometryColumnName" label="轨迹 Geometry" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="pointCountColumnName" label="观测数字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="startTimeColumnName" label="开始时间字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="endTimeColumnName" label="结束时间字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
        </div>
        <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
          <Input />
        </Form.Item>
      </Form>
    </Space>
  );
};

export default TrackReconstructInspector;
