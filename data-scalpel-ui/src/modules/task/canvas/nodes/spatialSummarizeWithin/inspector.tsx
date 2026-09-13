import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { temporalWindowLabel } from '../spatialCalendarWindow';
import {
  PlusOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import {
  Button,
  Checkbox,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_WITHIN_MAX_STATISTICS,
  CanvasNodeType,
  type JoinOutputColumn,
  type SpatialWithinStatistic,
  type SpatialSummarizeWithinConfiguration,
  type SpatialWithinRegions,
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
import {
  SpatialGroupSummaryEditor,
  SpatialTemporalSlicingEditor,
} from '../spatialAggregationShared';
import {
  createSpatialGroupSummary,
  createSpatialTemporalSlicing,
} from '../spatialAggregationOptions';

import { WithinStatisticEditor } from './statisticEditor';
import { WithinLinkedGroupEditor } from './groupEditor';
import { createWithinGroupResult } from './groupResult';
import { createWithinRegions, withinGridHelp } from './regions';
import { RegionsModal } from './RegionsModal';
import { spatialDistanceUnitOptions as lengthUnits, spatialAreaUnitOptions as areaUnits, spatialUnitHelp } from '../spatialUnits';
const fingerprint = (value: SpatialSummarizeWithinConfiguration) => JSON.stringify(value);

const SpatialSummarizeWithinInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialSummarizeWithin>) => {
  const [form] = Form.useForm<SpatialSummarizeWithinConfiguration>();
  const [areaFieldsOpen, setAreaFieldsOpen] = useState(false);
  const [regionsOpen, setRegionsOpen] = useState(false);
  const [pendingRegionsMode, setPendingRegionsMode] = useState<SpatialWithinRegions['mode']>(null);
  const [statisticsOpen, setStatisticsOpen] = useState(false);
  const [statisticsDraft, setStatisticsDraft] = useState<SpatialWithinStatistic[]>([]);
  const [groupOpen, setGroupOpen] = useState(false);
  const [temporalOpen, setTemporalOpen] = useState(false);
  const [groupDraft, setGroupDraft] = useState(
    node.configuration.groupSummary ?? createSpatialGroupSummary(),
  );
  const [groupResultDraft, setGroupResultDraft] = useState(node.configuration.groupResult ?? createWithinGroupResult());
  const [linkedDraft, setLinkedDraft] = useState(Boolean(node.configuration.groupResult && node.configuration.groupResult.mode !== 'LEGACY_FLAT'));
  const [temporalDraft, setTemporalDraft] = useState(
    node.configuration.temporalSlicing ?? createSpatialTemporalSlicing(),
  );
  const areaTableName = Form.useWatch('areaTableName', form) ?? '';
  const current = Form.useWatch<SpatialSummarizeWithinConfiguration>([], { form, preserve: true });
  const regions = current ? current.regions : node.configuration.regions;
  const gridMode = regions?.mode === 'PLANAR_GRID';
  const summaryTableName = Form.useWatch('summaryTableName', form) ?? '';
  const areaGeometryColumnName = Form.useWatch('areaGeometryColumnName', form) ?? '';
  const summaryGeometryColumnName = Form.useWatch('summaryGeometryColumnName', form) ?? '';
  const distanceMethod = Form.useWatch('distanceMethod', form) ?? 'PLANAR';
  const areaOutputColumns = Form.useWatch('areaOutputColumns', { form, preserve: true }) ?? node.configuration.areaOutputColumns;
  const statistics = Form.useWatch('statistics', { form, preserve: true }) ?? node.configuration.statistics;
  const groupSummary = Form.useWatch('groupSummary', { form, preserve: true });
  const groupResult = Form.useWatch('groupResult', { form, preserve: true });
  const linked = Boolean(groupSummary && groupResult && groupResult.mode !== 'LEGACY_FLAT');
  const temporalSlicing = Form.useWatch('temporalSlicing', { form, preserve: true });
  const tables = validation?.inputTables ?? [];
  const areaTable = tables.find((table) => table.name === areaTableName);
  const summaryTable = tables.find((table) => table.name === summaryTableName);
  const areaGeometry = spatialGeometryColumns(areaTable)
    .find((column) => column.name === areaGeometryColumnName);
  const summaryGeometry = spatialGeometryColumns(summaryTable)
    .find((column) => column.name === summaryGeometryColumnName);
  const geodesicGeometryInvalid = distanceMethod === 'GEODESIC'
    && (gridMode ? [summaryGeometry] : [areaGeometry, summaryGeometry]).some((column) => column?.geometry
      && (column.geometry.crs.authority !== 'EPSG'
        || column.geometry.crs.code !== 4326
        || column.geometry.dimension !== 'XY'));

  const normalized = (values: SpatialSummarizeWithinConfiguration) => ({
    ...values,
    areaTableName: values.areaTableName ?? '',
    areaGeometryColumnName: values.areaGeometryColumnName ?? '',
    summaryTableName: values.summaryTableName ?? '',
    summaryGeometryColumnName: values.summaryGeometryColumnName ?? '',
    includeEmptyAreas: values.includeEmptyAreas ?? true,
    distanceMethod: values.distanceMethod ?? null,
    lengthUnit: values.lengthUnit ?? 'SOURCE_CRS_UNIT',
    areaUnit: values.areaUnit ?? 'SQUARE_METERS',
    areaOutputColumns: values.areaOutputColumns ?? [],
    statistics: values.statistics ?? [],
    groupSummary: values.groupSummary ?? null,
    temporalSlicing: values.temporalSlicing ?? null,
    outputTableName: values.outputTableName?.trim() ?? '',
  });
  const markDirty = (values = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalized(values)) !== fingerprint(node.configuration));
  };
  const submit = (values: SpatialSummarizeWithinConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalized(values) });
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

  const suggestAreaFields = (tableName: string) => {
    if (areaOutputColumns.length > 0) return;
    const table = tables.find((item) => item.name === tableName);
    if (!table) return;
    form.setFieldValue('areaOutputColumns', table.columns.map((column): JoinOutputColumn => ({
      sourceSide: 'LEFT',
      sourceColumnName: column.name,
      outputColumnName: column.name,
      included: true,
    })));
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<SpatialSummarizeWithinConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => markDirty()}
      >
        <Form.Item label={<Space>汇总区域<ContextHelp ariaLabel="区域来源说明" content={withinGridHelp} /></Space>}>
          <Select aria-label="汇总区域来源" value={regions ? regions.mode : 'AREA_TABLE'}
            status={regions && !regions.mode ? 'error' : undefined}
            options={[{ value: 'AREA_TABLE', label: '区域表' }, { value: 'PLANAR_GRID', label: '规则格网' }]}
            onChange={setPendingRegionsMode} />
        </Form.Item>
        {gridMode && <div className="canvas-processor-section-header">
          <Typography.Text type={regions?.binSize == null || regions.binSize <= 0 ? 'danger' : undefined}>
            {regions?.binShape === 'HEXAGON' ? '六边形 · 对边距离' : '方格 · 边长'} {regions?.binSize ?? '待配置'}
          </Typography.Text>
          <Button size="small" aria-label="设置汇总格网" icon={<SettingOutlined />} onClick={() => setRegionsOpen(true)}>设置</Button>
        </div>}
        <div className="canvas-spatial-pair-grid">
          {!gridMode && <Form.Item name="areaTableName" label="统计区域" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, areaTableName)}
              onChange={(value) => suggestAreaFields(value)}
            />
          </Form.Item>}
          <Form.Item name="summaryTableName" label="被汇总要素" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, summaryTableName)}
            />
          </Form.Item>
          {!gridMode && <Form.Item name="areaGeometryColumnName" label="区域 Geometry" rules={[{ required: true }]}>
            <Select
              options={spatialColumnOptions(
                areaTable?.columns ?? [], areaGeometryColumnName,
                (column) => column.fieldType === 'GEOMETRY',
              )}
            />
          </Form.Item>}
          <Form.Item name="summaryGeometryColumnName" label="要素 Geometry" rules={[{ required: true }]}>
            <Select
              options={spatialColumnOptions(
                summaryTable?.columns ?? [], summaryGeometryColumnName,
                (column) => column.fieldType === 'GEOMETRY',
              )}
            />
          </Form.Item>
        </div>
        {!gridMode && areaGeometry?.geometry && summaryGeometry?.geometry
          && (areaGeometry.geometry.crs.code !== summaryGeometry.geometry.crs.code
            || areaGeometry.geometry.dimension !== summaryGeometry.geometry.dimension) && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            两侧 CRS 或坐标维度不一致，请先进行空间转换。
          </Typography.Text>
        )}
        <Form.Item name="includeEmptyAreas" label="保留空区域" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item
          name="distanceMethod"
          label={(
            <span className="canvas-inspector-field-label">
              测量方法
              <ContextHelp
                ariaLabel="区域内测量说明"
                content="LENGTH_WITHIN 与 AREA_WITHIN 会先裁剪到区域内部，再计算片段长度或面积。测地线要求区域与被汇总 Geometry 都是 EPSG:4326 XY。"
              />
            </span>
          )}
        >
          <Segmented block options={[
            { value: 'PLANAR', label: '平面' },
            { value: 'GEODESIC', label: '测地线' },
          ]} />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="lengthUnit" label={<Space>长度单位<ContextHelp ariaLabel="长度及面积单位说明" content={spatialUnitHelp} /></Space>}><Select options={lengthUnits} /></Form.Item>
          <Form.Item name="areaUnit" label="面积单位"><Select options={areaUnits} /></Form.Item>
        </div>
        {geodesicGeometryInvalid && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            区域与被汇总 Geometry 都必须是 EPSG:4326 XY；请先进行空间转换或维度处理。
          </Typography.Text>
        )}

        {!gridMode && <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>区域输出字段</Typography.Text>
            <Tag>{areaOutputColumns.filter((item) => item.included).length}/{areaOutputColumns.length}</Tag>
          </Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setAreaFieldsOpen(true)}>设置</Button>
        </div>}
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>统计项</Typography.Text><Tag>{statistics.length}</Tag></Space>
          <Button size="small" aria-label="设置区域统计项" icon={<SettingOutlined />} onClick={() => {
            const current: SpatialSummarizeWithinConfiguration = form.getFieldsValue(true);
            setStatisticsDraft((current.statistics ?? []).map(item => ({ ...item })));
            setStatisticsOpen(true);
          }}>设置</Button>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>分组汇总</Typography.Text>
            <Typography.Text type="secondary">{groupSummary
              ? `${groupSummary.groupByColumnName || '待选字段'} · ${linked ? '关联双表' : '旧版扁平'}` : '未启用'}</Typography.Text>
          </Space>
          <Space size={4}>
            <Switch
              size="small"
              checked={Boolean(groupSummary)}
              aria-label="启用分组汇总"
              onChange={(checked) => {
                form.setFieldValue('groupSummary', checked ? groupDraft : null);
                markDirty({ ...form.getFieldsValue(true), groupSummary: checked ? groupDraft : null });
              }}
            />
            <Button size="small" aria-label="设置区域分组结果" disabled={!groupSummary} onClick={() => {
              setGroupDraft(form.getFieldValue('groupSummary') ?? groupDraft);
              const saved = form.getFieldValue('groupResult');
              setGroupResultDraft(saved ?? createWithinGroupResult());
              setLinkedDraft(Boolean(saved && saved.mode !== 'LEGACY_FLAT'));
              setGroupOpen(true);
            }}>设置</Button>
          </Space>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>时间切片</Typography.Text>
            <Typography.Text type="secondary">
              {temporalSlicing ? temporalWindowLabel(temporalSlicing) : '未启用'}
            </Typography.Text>
          </Space>
          <Space size={4}>
            <Switch
              size="small"
              aria-label="启用时间切片"
              checked={Boolean(temporalSlicing)}
              onChange={(checked) => {
                form.setFieldValue('temporalSlicing', checked ? temporalDraft : null);
                markDirty({ ...form.getFieldsValue(true), temporalSlicing: checked ? temporalDraft : null });
              }}
            />
            <Button size="small" aria-label="设置时间切片" disabled={!temporalSlicing} onClick={() => {
              const values: SpatialSummarizeWithinConfiguration = form.getFieldsValue(true);
              setTemporalDraft(structuredClone(values.temporalSlicing ?? createSpatialTemporalSlicing()));
              setTemporalOpen(true);
            }}>设置</Button>
          </Space>
        </div>
        {linked && <Typography.Text type="secondary" ellipsis={{ tooltip: true }}>
          关联组表：{groupResult?.outputTableName || '待配置'}
        </Typography.Text>}
        <Form.Item name="outputTableName" label={linked ? '主结果表名' : '输出表名'} rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 district_summary" />
        </Form.Item>

        <Modal open={areaFieldsOpen} width={760} title="设置区域输出字段" okText="完成"
          onOk={() => setAreaFieldsOpen(false)} onCancel={() => setAreaFieldsOpen(false)}>
          <Space orientation="vertical" size={6} style={{ width: '100%' }}>
            {areaOutputColumns.map((column, index) => (
              <div className="canvas-spatial-projection-row" key={`${column.sourceColumnName}-${index}`}>
                <Checkbox
                  checked={column.included}
                  onChange={(event) => {
                    const next = areaOutputColumns.map((item, itemIndex) => itemIndex === index
                      ? { ...item, included: event.target.checked } : item);
                    form.setFieldValue('areaOutputColumns', next);
                    markDirty({ ...form.getFieldsValue(true), areaOutputColumns: next });
                  }}
                />
                <Typography.Text ellipsis>{column.sourceColumnName}</Typography.Text>
                <Input
                  value={column.outputColumnName}
                  disabled={!column.included}
                  onChange={(event) => {
                    const next = areaOutputColumns.map((item, itemIndex) => itemIndex === index
                      ? { ...item, outputColumnName: event.target.value } : item);
                    form.setFieldValue('areaOutputColumns', next);
                    markDirty({ ...form.getFieldsValue(true), areaOutputColumns: next });
                  }}
                />
              </div>
            ))}
            {areaOutputColumns.length === 0 && <Typography.Text type="secondary">选择区域表后生成字段。</Typography.Text>}
          </Space>
        </Modal>

        <Modal open={statisticsOpen} destroyOnHidden width={860} title="设置区域统计项" okText="完成" cancelText="取消"
          onOk={() => {
            form.setFieldValue('statistics', statisticsDraft);
            markDirty({ ...form.getFieldsValue(true), statistics: statisticsDraft });
            setStatisticsOpen(false);
          }} onCancel={() => setStatisticsOpen(false)}>
          <div className="canvas-spatial-modal-toolbar">
            <Typography.Text type="secondary">统计值不会显示在 Canvas 卡片或日志中。</Typography.Text>
            <Button
              size="small" type="primary" icon={<PlusOutlined />}
              disabled={statisticsDraft.length >= CANVAS_SPATIAL_WITHIN_MAX_STATISTICS}
              onClick={() => {
                const next = [...statisticsDraft, {
                  statisticId: crypto.randomUUID(), kind: 'COUNT' as const,
                  sourceColumnName: null, outputColumnName: 'feature_count',
                }];
                setStatisticsDraft(next);
              }}
            >添加统计</Button>
          </div>
          <WithinStatisticEditor value={statisticsDraft} columns={summaryTable?.columns ?? []}
            lineOrPolygon={summaryGeometry?.geometry ? ['LINESTRING', 'MULTILINESTRING', 'POLYGON', 'MULTIPOLYGON']
              .includes(summaryGeometry.geometry.kind) : undefined}
            onChange={setStatisticsDraft} />
        </Modal>

        <Modal open={groupOpen} width={680} title="设置分组汇总" okText="保存草稿"
          onOk={() => {
            const nextResult = { ...groupResultDraft, mode: linkedDraft ? 'LINKED_TABLES' as const : 'LEGACY_FLAT' as const };
            form.setFieldValue('groupSummary', groupDraft);
            form.setFieldValue('groupResult', nextResult);
            markDirty({ ...form.getFieldsValue(true), groupSummary: groupDraft, groupResult: nextResult });
            setGroupOpen(false);
          }} onCancel={() => {
            setGroupDraft(form.getFieldValue('groupSummary') ?? createSpatialGroupSummary());
            setGroupOpen(false);
          }}>
          <Space orientation="vertical" size={12} style={{ width: '100%' }}>
          <Select aria-label="分组结果模式" style={{ width: '100%' }} value={linkedDraft ? 'LINKED' : 'LEGACY'}
            options={[{ value: 'LINKED', label: '主表 + 关联组表（推荐）' }, { value: 'LEGACY', label: '旧版扁平分组表' }]}
            onChange={mode => Modal.confirm({ title: '切换分组结果模式？',
              content: '将改变结果表数量、行粒度及少数/多数语义。已有配置保留，请检查下游使用的表和字段。',
              okText: '确认切换', onOk: () => setLinkedDraft(mode === 'LINKED'),
            })} />
          {linkedDraft ? <WithinLinkedGroupEditor group={groupDraft} result={groupResultDraft}
            generatedAreaKey={gridMode}
            areaColumns={areaTable?.columns ?? []} summaryColumns={summaryTable?.columns ?? []}
            mainTableName={form.getFieldValue('outputTableName') ?? ''} inputTableNames={tables.map(t => t.name)}
            onGroupChange={setGroupDraft} onResultChange={setGroupResultDraft} /> : <SpatialGroupSummaryEditor
            value={groupDraft}
            columns={summaryTable?.columns ?? []}
            onChange={setGroupDraft}
          />}
          </Space>
        </Modal>

        <Modal open={temporalOpen} width={620} title="设置时间切片" okText="保存草稿"
          onOk={() => {
            form.setFieldValue('temporalSlicing', temporalDraft);
            markDirty({ ...form.getFieldsValue(true), temporalSlicing: temporalDraft });
            setTemporalOpen(false);
          }} cancelText="取消" onCancel={() => {
            const values: SpatialSummarizeWithinConfiguration = form.getFieldsValue(true);
            setTemporalDraft(structuredClone(values.temporalSlicing ?? createSpatialTemporalSlicing()));
            setTemporalOpen(false);
          }}>
          <SpatialTemporalSlicingEditor
            value={temporalDraft}
            columns={summaryTable?.columns ?? []}
            onChange={setTemporalDraft}
          />
        </Modal>
      </Form>
      {regionsOpen && <RegionsModal initial={regions ?? createWithinRegions()} onCancel={() => setRegionsOpen(false)}
        onSave={value => {
          form.setFieldValue('regions', value);
          markDirty();
          setRegionsOpen(false);
        }} />}
      <Modal open={pendingRegionsMode !== null} title="切换汇总区域来源？" okText="确认切换区域" cancelText="取消"
        onCancel={() => setPendingRegionsMode(null)} onOk={() => {
          if (pendingRegionsMode) {
            form.setFieldValue('regions', { ...createWithinRegions(), ...regions, mode: pendingRegionsMode });
            markDirty();
          }
          setPendingRegionsMode(null);
        }}>
        区域来源改变会影响结果字段、区域身份和统计结果。区域表、字段投影和格网参数都保留为各自草稿，不自动清空。
      </Modal>
    </Space>
  );
};

export default SpatialSummarizeWithinInspector;
