import { createUuid } from '../../../../../shared/browser/createUuid';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, DownOutlined, PlusOutlined, SettingOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_CENTER_MAX_ANALYSES,
  CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialCenterDispersionAnalysis,
  type SpatialCenterDispersionConfiguration,
  type SpatialCenterDispersionKind,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import FeatureColumnsModal from './FeatureColumnsModal';

const analysisKinds: SpatialCenterDispersionKind[] = [
  'MEAN_CENTER', 'MEDIAN_CENTER', 'CENTRAL_FEATURE',
  'STANDARD_DISTANCE', 'DIRECTIONAL_ELLIPSE',
];
const analysisLabels: Record<SpatialCenterDispersionKind, string> = {
  MEAN_CENTER: '平均中心',
  MEDIAN_CENTER: '中位中心',
  CENTRAL_FEATURE: '中央要素',
  STANDARD_DISTANCE: '标准距离（扩展）',
  DIRECTIONAL_ELLIPSE: '方向椭圆',
};
const defaultOutputNames: Record<SpatialCenterDispersionKind, string> = {
  MEAN_CENTER: 'mean_center',
  MEDIAN_CENTER: 'median_center',
  CENTRAL_FEATURE: 'central_feature',
  STANDARD_DISTANCE: 'standard_distance',
  DIRECTIONAL_ELLIPSE: 'directional_ellipse',
};
const analysisHelp = (
  <Space orientation="vertical" size={6}>
    <span><strong>平均中心：</strong>按坐标算术平均定位中心，可选权重。</span>
    <span><strong>中位中心：</strong>新模式使用带误差停止准则的修正 Weiszfeld 迭代；达到上限但未收敛时失败，不返回固定轮数的猜测。</span>
    <span><strong>中央要素：</strong>线面按质心计算加权距离和，结果返回选中的原 Geometry 和 ID；平局按 ID 原字段类型排序。</span>
    <span><strong>标准距离：</strong>用圆表达点相对平均中心的离散程度。</span>
    <span><strong>方向椭圆：</strong>用椭圆表达主要方向与两个轴向的离散程度。</span>
    <span>新模式单组最多 100000 个有效要素，包含中央要素时最多 5000 个；总顶点最多 100 万。权重必须有限非负，NULL 权重不参与，全零组不输出。退化圆/椭圆输出 Empty Polygon，1/2/3σ 不保证固定覆盖率。</span>
    <span>本轮不输出平均/中位/椭圆时间。椭圆权重公式与官方时间细节仍待对照，不等于完整 GA 对齐。</span>
  </Space>
);
const numericTypes = new Set([
  'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
]);

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

const fingerprint = (value: SpatialCenterDispersionConfiguration) => JSON.stringify(value);

const SpatialCenterDispersionInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialCenterDispersion>) => {
  const [form] = Form.useForm<SpatialCenterDispersionConfiguration>();
  const [analysesOpen, setAnalysesOpen] = useState(false);
  const [featureColumnsOpen, setFeatureColumnsOpen] = useState(false);
  const [analysisDraft, setAnalysisDraft] = useState<SpatialCenterDispersionAnalysis[] | null>(null);
  const resultMode = Form.useWatch('resultMode', { form, preserve: true });
  const separate = resultMode === 'ANALYSIS_TABLES';
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const groupByColumns = Form.useWatch('groupByColumns', form) ?? [];
  const featureIdColumnName = Form.useWatch('featureIdColumnName', { form, preserve: true }) ?? '';
  const weightColumnName = Form.useWatch('weightColumnName', form) ?? '';
  const savedAnalyses: SpatialCenterDispersionAnalysis[] = Form.useWatch('analyses', { form, preserve: true }) ?? [];
  const analyses = analysisDraft ?? savedAnalyses;
  const centralAnalysis = savedAnalyses.find((a: SpatialCenterDispersionAnalysis) => a.kind === 'CENTRAL_FEATURE');
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const pointGeometry = spatialGeometryColumns(sourceTable)
    .find((column) => column.name === pointGeometryColumnName);
  const requiresFeatureId = analyses.some((analysis) => analysis.kind === 'CENTRAL_FEATURE');

  const normalize = (
    value: SpatialCenterDispersionConfiguration,
  ): SpatialCenterDispersionConfiguration => ({
    ...value,
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    featureIdColumnName: value.featureIdColumnName || null,
    groupByColumns: value.groupByColumns ?? [],
    weightColumnName: value.weightColumnName || null,
    analyses: value.analyses ?? [],
    outputTableName: value.outputTableName?.trim() ?? '',
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(node.configuration));
  };
  const submit = (value: SpatialCenterDispersionConfiguration) => {
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
  const updateAnalyses = (value: SpatialCenterDispersionAnalysis[]) => {
    if (analysisDraft != null) { setAnalysisDraft(value); return; }
    form.setFieldValue('analyses', value);
    markDirty({ ...form.getFieldsValue(true), analyses: value });
  };
  const updateAnalysis = (index: number, value: SpatialCenterDispersionAnalysis) => {
    updateAnalyses(analyses.map((item, itemIndex) => itemIndex === index ? value : item));
  };
  const moveAnalysis = (from: number, to: number) => {
    const next = [...analyses];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    updateAnalyses(next);
  };
  const addAnalysis = () => {
    const used = new Set(analyses.map((analysis) => analysis.kind));
    const kind = analysisKinds.find((candidate) => !used.has(candidate));
    if (!kind) return;
    updateAnalyses([...analyses, {
      analysisId: createUuid(),
      kind,
      outputColumnName: defaultOutputNames[kind],
      standardDeviations: kind === 'STANDARD_DISTANCE' || kind === 'DIRECTIONAL_ELLIPSE' ? 1 : null,
      ...(separate ? { outputTableName: '' } : {}),
    }]);
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<SpatialCenterDispersionConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => markDirty()}
      >
        <Form.Item label="结果模式">
          <Select aria-label="中心结果模式" value={resultMode ?? 'LEGACY_WIDE'}
            options={[{ value: 'ANALYSIS_TABLES', label: '各分析独立结果表' }, { value: 'LEGACY_WIDE', label: '旧版多几何宽表' }]}
            onChange={value => Modal.confirm({ title: '切换中心分析结果模式？',
              content: '新模式改变结果表结构、使用收敛检查并允许线面质心分析；空组与退化输出语义也不同。旧配置和未启用设置会保留，请检查下游。',
              okText: '确认切换', cancelText: '取消', onOk: () => { form.setFieldValue('resultMode', value); markDirty(); } })} />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, sourceTableName)}
            />
          </Form.Item>
          <Form.Item name="pointGeometryColumnName" label="Geometry" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              columns,
              pointGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY' && (separate || column.geometry?.kind === 'POINT'),
            )} />
          </Form.Item>
        </div>
        {separate && pointGeometry?.geometry && pointGeometry.geometry.kind !== 'POINT' && <Typography.Text type="secondary" className="canvas-field-inline-warning">按要素质心分析，中央要素返回原 Geometry。</Typography.Text>}
        {pointGeometry?.geometry?.crs.authority === 'EPSG' && pointGeometry.geometry.crs.code === 4326 && (
          <Typography.Text type="warning" className="canvas-field-inline-warning">
            中心与离散统计需要投影坐标，请先使用空间转换。
          </Typography.Text>
        )}
        <Form.Item name="groupByColumns" label="分组字段">
          <Select
            mode="multiple" maxCount={CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS}
            showSearch optionFilterProp="label" allowClear
            options={multipleColumnOptions(columns, groupByColumns)}
            placeholder="不选择表示全局统计"
          />
        </Form.Item>
        <Form.Item name="weightColumnName" label="权重字段（可选）">
          <Select
            showSearch optionFilterProp="label" allowClear
            options={spatialColumnOptions(
              columns, weightColumnName,
              (column) => numericTypes.has(column.fieldType),
            )}
          />
        </Form.Item>
        {requiresFeatureId && (
          <Form.Item
            name="featureIdColumnName"
            label="要素唯一字段"
            rules={[{ required: true, message: '中央要素分析需要唯一字段' }]}
          >
            <Select
              showSearch optionFilterProp="label"
              options={spatialColumnOptions(
                columns, featureIdColumnName, (column) => column.fieldType !== 'GEOMETRY',
              )}
            />
          </Form.Item>
        )}
        {separate && centralAnalysis && <div className="canvas-processor-section-header">
          <Typography.Text>原要素字段 · {centralAnalysis.centralFeatureColumns == null ? '分组与 ID（旧默认）' : `${centralAnalysis.centralFeatureColumns.filter(c => c.included).length} 个`}</Typography.Text>
          <Button size="small" aria-label="设置中央要素原始字段" icon={<SettingOutlined />} onClick={() => setFeatureColumnsOpen(true)}>设置</Button>
        </div>}
        {featureColumnsOpen && centralAnalysis && <FeatureColumnsModal value={centralAnalysis.centralFeatureColumns} columns={columns}
          geometry={pointGeometryColumnName} outputGeometry={centralAnalysis.outputColumnName} onCancel={() => setFeatureColumnsOpen(false)}
          onSave={fields => { updateAnalyses(savedAnalyses.map((a: SpatialCenterDispersionAnalysis) => a.analysisId === centralAnalysis.analysisId ? { ...a, centralFeatureColumns: fields } : a)); setFeatureColumnsOpen(false); }} />}
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>分析项</Typography.Text><Tag>{analyses.length}</Tag>
            <ContextHelp
              ariaLabel="中心与离散分析说明"
              content={analysisHelp}
              presentation="popover"
            />
          </Space>
          <Button size="small" aria-label="设置中心分析项" icon={<SettingOutlined />} onClick={() => { setAnalysisDraft(savedAnalyses.map(a => ({ ...a }))); setAnalysesOpen(true); }}>设置</Button>
        </div>
        <Table size="small" rowKey="analysisId" pagination={false} dataSource={savedAnalyses} columns={[
          { title: '分析', dataIndex: 'kind', render: (kind: SpatialCenterDispersionKind) => analysisLabels[kind] },
          { title: separate ? '结果表' : 'Geometry 字段', ellipsis: true, render: (_, a: SpatialCenterDispersionAnalysis) => {
            const name = separate ? a.outputTableName : a.outputColumnName;
            return name || <Typography.Text type="danger">待设置</Typography.Text>;
          } },
          { title: 'σ', width: 44, render: (_, a: SpatialCenterDispersionAnalysis) => a.standardDeviations ?? '—' },
        ]} />
        {!separate && <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 city_center_dispersion" />
        </Form.Item>}

        <Modal
          open={analysesOpen}
          width={860}
          title="设置中心与离散分析"
          okText="保存草稿"
          cancelText="取消"
          onOk={() => { form.setFieldValue('analyses', analysisDraft ?? savedAnalyses); setAnalysisDraft(null); setAnalysesOpen(false); markDirty(); }}
          onCancel={() => { setAnalysisDraft(null); setAnalysesOpen(false); }}
        >
          <div className="canvas-spatial-modal-toolbar">
            <Typography.Text type="secondary">每种统计类型最多配置一次。</Typography.Text>
            <Button
              type="primary" size="small" icon={<PlusOutlined />}
              disabled={analyses.length >= Math.min(
                CANVAS_SPATIAL_CENTER_MAX_ANALYSES, analysisKinds.length,
              )}
              onClick={addAnalysis}
            >添加分析</Button>
          </div>
          <Space orientation="vertical" size={6} style={{ width: '100%' }}>
            {analyses.map((analysis, index) => {
              const deviation = analysis.kind === 'STANDARD_DISTANCE'
                || analysis.kind === 'DIRECTIONAL_ELLIPSE';
              const used = new Set(analyses.filter((_, itemIndex) => itemIndex !== index)
                .map((item) => item.kind));
              return (
                <div className={separate ? 'canvas-center-analysis-row' : 'canvas-spatial-analysis-row'} key={analysis.analysisId}>
                  <Select
                    value={analysis.kind}
                    options={analysisKinds.map((kind) => ({
                      value: kind, label: analysisLabels[kind], disabled: used.has(kind),
                    }))}
                    onChange={(kind: SpatialCenterDispersionKind) => updateAnalysis(index, {
                      ...analysis,
                      kind,
                      standardDeviations: kind === 'STANDARD_DISTANCE'
                        || kind === 'DIRECTIONAL_ELLIPSE' ? 1 : null,
                    })}
                  />
                  {separate && <Input aria-label={`结果表 ${index + 1}`} value={analysis.outputTableName ?? ''} status={analysis.outputTableName?.trim() ? undefined : 'error'} placeholder="结果表名"
                    onChange={event => updateAnalysis(index, { ...analysis, outputTableName: event.target.value })} />}
                  <Input
                    aria-label={`Geometry 字段 ${index + 1}`}
                    value={analysis.outputColumnName}
                    placeholder="输出字段"
                    onChange={(event) => updateAnalysis(index, {
                      ...analysis, outputColumnName: event.target.value,
                    })}
                  />
                  <Select
                    allowClear
                    disabled={!deviation}
                    value={analysis.standardDeviations}
                    placeholder="标准差"
                    options={[1, 2, 3].map((value) => ({ value, label: `${value}σ` }))}
                    onChange={(standardDeviations) => updateAnalysis(index, {
                      ...analysis, standardDeviations: standardDeviations ?? null,
                    })}
                  />
                  <Space size={0}>
                    <Button type="text" size="small" aria-label={`上移分析 ${index + 1}`} icon={<UpOutlined />} disabled={index === 0}
                      onClick={() => moveAnalysis(index, index - 1)} />
                    <Button type="text" size="small" aria-label={`下移分析 ${index + 1}`} icon={<DownOutlined />}
                      disabled={index === analyses.length - 1}
                      onClick={() => moveAnalysis(index, index + 1)} />
                    <Button type="text" danger size="small" aria-label={`删除分析 ${index + 1}`} icon={<DeleteOutlined />}
                      onClick={() => Modal.confirm({ title: `删除${analysisLabels[analysis.kind]}？`, content: '该分析的结果表与字段配置将一并删除。', okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
                        onOk: () => updateAnalyses(analyses.filter((_, itemIndex) => itemIndex !== index)) })} />
                  </Space>
                </div>
              );
            })}
            {analyses.length === 0 && (
              <Typography.Text type="secondary">至少添加一个中心或离散分析项。</Typography.Text>
            )}
          </Space>
        </Modal>
      </Form>
    </Space>
  );
};

export default SpatialCenterDispersionInspector;
