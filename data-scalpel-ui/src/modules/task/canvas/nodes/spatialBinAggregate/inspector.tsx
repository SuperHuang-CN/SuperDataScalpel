import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { temporalWindowLabel } from '../spatialCalendarWindow';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  SettingOutlined,
  UpOutlined,
} from '@ant-design/icons';
import {
  Button,
  Form,
  Input,
  InputNumber,
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
  CANVAS_SPATIAL_BIN_MAX_STATISTICS,
  CanvasNodeType,
  type SpatialBinAggregateConfiguration,
  type SpatialBinShape,
  type SpatialBinSizeSemantics,
  type SpatialBinStatistic,
  type SpatialBinStatisticKind,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import {
  SpatialGroupSummaryEditor,
  SpatialTemporalSlicingEditor,
} from '../spatialAggregationShared';
import {
  createSpatialGroupSummary,
  createSpatialTemporalSlicing,
} from '../spatialAggregationOptions';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { trackDistanceUnitOptions } from '../trackOptions';
import { spatialUnitHelp } from '../spatialUnits';
import { binSizeLabel } from './binSizeSemantics';
import { PlanarGridModal } from './PlanarGridModal';
import { planarGridHelp } from './planarGrid';

const statisticKinds: Array<{ value: SpatialBinStatisticKind; label: string }> = [
  { value: 'COUNT', label: 'COUNT · 点数' },
  { value: 'COUNT_FIELD', label: 'COUNT_FIELD · 非空数' },
  { value: 'ANY', label: 'ANY · 字符串样本' },
  ...(['SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE'] as const).map(value => ({ value, label: value })),
];

const fingerprint = (value: SpatialBinAggregateConfiguration) => JSON.stringify(value);

const SpatialBinAggregateInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialBinAggregate>) => {
  const [form] = Form.useForm<SpatialBinAggregateConfiguration>();
  const [statisticsOpen, setStatisticsOpen] = useState(false);
  const [statisticsDraft, setStatisticsDraft] = useState<SpatialBinStatistic[]>([]);
  const [statisticFieldDrafts, setStatisticFieldDrafts] = useState<Record<string, string | null>>({});
  const [groupOpen, setGroupOpen] = useState(false);
  const [temporalOpen, setTemporalOpen] = useState(false);
  const [fieldsOpen, setFieldsOpen] = useState(false);
  const [planarGridOpen, setPlanarGridOpen] = useState(false);
  const [groupDraft, setGroupDraft] = useState(
    node.configuration.groupSummary ?? createSpatialGroupSummary(),
  );
  const [temporalDraft, setTemporalDraft] = useState(
    node.configuration.temporalSlicing ?? createSpatialTemporalSlicing(),
  );
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const current = Form.useWatch<SpatialBinAggregateConfiguration>([], { form, preserve: true });
  const binShape = current?.binShape ?? node.configuration.binShape;
  const h3 = current ? current.h3 : node.configuration.h3;
  const planarGrid = current ? current.planarGrid : node.configuration.planarGrid;
  const binSizeSemantics = Form.useWatch('binSizeSemantics', { form, preserve: true })
    ?? node.configuration.binSizeSemantics ?? 'LEGACY_SIDE_LENGTH';
  const statistics = Form.useWatch('statistics', { form, preserve: true }) ?? [];
  const groupSummary = Form.useWatch('groupSummary', { form, preserve: true });
  const temporalSlicing = Form.useWatch('temporalSlicing', { form, preserve: true });
  const includeEmptyBins = Form.useWatch('includeEmptyBins', form) ?? false;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const pointGeometry = spatialGeometryColumns(sourceTable)
    .find((column) => column.name === pointGeometryColumnName);

  const normalize = (
    value: SpatialBinAggregateConfiguration,
  ): SpatialBinAggregateConfiguration => ({
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    binShape: value.binShape ?? null,
    binSize: value.binSize ?? 0,
    binSizeUnit: value.binSizeUnit ?? 'METERS',
    binSizeSemantics: value.binSizeSemantics ?? 'LEGACY_SIDE_LENGTH',
    includeEmptyBins: value.includeEmptyBins ?? false,
    statistics: value.statistics ?? [],
    groupSummary: value.groupSummary ?? null,
    temporalSlicing: value.temporalSlicing ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    binIdColumnName: value.binIdColumnName?.trim() ?? '',
    binGeometryColumnName: value.binGeometryColumnName?.trim() ?? '',
    ...('h3' in value ? { h3: value.h3 } : {}),
    ...('planarGrid' in value ? { planarGrid: value.planarGrid } : {}),
  });
  const appliedH3Comment = fingerprint(normalize(current ?? node.configuration)) === fingerprint(normalize(node.configuration))
    ? validation?.outputTables.find((table) => table.name === node.configuration.outputTableName)
      ?.columns.find((column) => column.name === node.configuration.binIdColumnName)?.comment
    : undefined;
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: SpatialBinAggregateConfiguration) => {
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
  const updateConfiguration = <K extends keyof SpatialBinAggregateConfiguration>(
    name: K,
    value: SpatialBinAggregateConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };
  const updateStatistic = (index: number, value: SpatialBinStatistic) => {
    setStatisticsDraft(statisticsDraft.map((item, itemIndex) => (
      itemIndex === index ? value : item
    )));
  };
  const moveStatistic = (from: number, to: number) => {
    const next = [...statisticsDraft];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    setStatisticsDraft(next);
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<SpatialBinAggregateConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={normalize(node.configuration)}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => markDirty()}
      >
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="sourceTableName" label="来源点表" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, sourceTableName)}
            />
          </Form.Item>
          <Form.Item name="pointGeometryColumnName" label="Point Geometry" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label"
              options={spatialColumnOptions(
                columns,
                pointGeometryColumnName,
                (column) => column.fieldType === 'GEOMETRY' && column.geometry?.kind === 'POINT',
              )}
            />
          </Form.Item>
        </div>
        {binShape !== 'H3' && pointGeometry?.geometry?.crs.authority === 'EPSG' && pointGeometry.geometry.crs.code === 4326 && (
          <Typography.Text type="warning" className="canvas-field-inline-warning">
            经纬度不能直接作为格网长度单位，请先使用空间转换得到投影坐标。
          </Typography.Text>
        )}
        {binShape === 'H3' && pointGeometry && (pointGeometry.geometry?.crs.authority !== 'EPSG' || pointGeometry.geometry.crs.code !== 4326) && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            H3 需要 EPSG:4326 点表，请先使用空间转换。
          </Typography.Text>
        )}
        <Form.Item label="格网形状" required validateStatus={!binShape ? 'error' : undefined}
          help={!binShape ? '请选择格网形状' : undefined}>
          <Segmented<SpatialBinShape | ''> block aria-label="格网形状" value={binShape ?? ''} options={[
            { value: 'SQUARE', label: '方格' },
            { value: 'HEXAGON', label: '六边形' },
            { value: 'H3', label: 'H3' },
          ]} onChange={(next) => { if (!next) return; Modal.confirm({
            title: '切换格网形状？',
            content: '格网 ID、边界和统计分组会变化；各模式参数保留，切换后请检查坐标系及大小。',
            okText: '确认切换', cancelText: '取消', onOk: () => {
              if (next === 'H3' && !form.getFieldValue('h3')) {
                form.setFieldValue('h3', { mode: 'RESOLUTION', resolution: null });
              }
              updateConfiguration('binShape', next);
            },
          }); }} />
        </Form.Item>
        {binShape !== 'H3' && <div className="canvas-processor-section-header">
          <Space size={4}><Typography.Text strong>范围与对齐</Typography.Text>
            <Typography.Text type="secondary">{planarGrid ? planarGrid.extent?.mode === 'EXPLICIT_BOUNDS' ? '显式业务范围' : '指定原点 · 数据范围' : '旧版原点 · 数据范围'}</Typography.Text>
            <ContextHelp ariaLabel="平面格网配置说明" content={planarGridHelp} />
          </Space>
          <Button size="small" aria-label="设置格网范围与对齐" icon={<SettingOutlined />} onClick={() => setPlanarGridOpen(true)}>设置</Button>
        </div>}
        {binShape === 'H3' && <>
          <Form.Item label={<span className="canvas-inspector-field-label">H3 大小方式<ContextHelp
            ariaLabel="H3 大小说明"
            content="分辨率为 0～15，越大格网越细。近似尺寸按 √3 × H3 平均边长选最接近级别，不保证等于 ArcGIS 的级别选择；实际边长随位置变化，并存在五边形。H3 使用 EPSG:4326，不使用平面格网原点。"
          /></span>} required>
            <Select aria-label="H3 大小方式" value={h3?.mode} options={[
              { value: 'RESOLUTION', label: '分辨率（0～15）' },
              { value: 'APPROXIMATE_SIZE', label: '近似对边距离' },
            ]} onChange={(mode) => updateConfiguration('h3', { mode, resolution: h3?.resolution ?? null })} />
          </Form.Item>
          {h3?.mode === 'RESOLUTION' && <Form.Item label="H3 分辨率" required
            validateStatus={h3.resolution == null || h3.resolution < 0 || h3.resolution > 15 ? 'error' : undefined}
            help={h3.resolution == null || h3.resolution < 0 || h3.resolution > 15 ? '请输入 0～15 的整数' : undefined}>
            <InputNumber aria-label="H3 分辨率" precision={0} min={0} max={15} value={h3.resolution}
              onChange={(resolution) => updateConfiguration('h3', { mode: 'RESOLUTION', resolution })} />
          </Form.Item>}
        </>}
        {(binShape !== 'H3' || h3?.mode === 'APPROXIMATE_SIZE') && <Form.Item
          label={<span className="canvas-inspector-field-label">{binShape === 'H3' ? '近似对边距离' : binSizeLabel({ binShape, binSizeSemantics })}<ContextHelp
            ariaLabel="格网大小说明"
            content={`${binShape === 'H3' ? '填写正数和线性单位，应用后由 Compiler 解析实际 H3 级别。该距离只用于选级别，不是每个格网的实际宽度。' : '方格按边长；六边形推荐按对边距离 d，边长=d/√3，面积=√3/2×d²。旧版六边形继续按边长解释。范围可从来源推导或显式设置，编译不读取坐标。'}${spatialUnitHelp}`}
          /></span>}
          required
        >
          <Space.Compact block>
            <Form.Item name="binSize" noStyle rules={[{ required: true }]}>
              <InputNumber min={Number.MIN_VALUE} style={{ width: '55%' }} />
            </Form.Item>
            <Form.Item name="binSizeUnit" noStyle rules={[{ required: true }]}>
              <Select options={trackDistanceUnitOptions.map((option) => ({
                ...option, disabled: binShape === 'H3' && option.value === 'SOURCE_CRS_UNIT',
              }))} style={{ width: '45%' }} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>}
        {binShape === 'H3' && h3?.mode === 'APPROXIMATE_SIZE' && <Typography.Text
          type="secondary" className="canvas-field-inline-warning">
          {appliedH3Comment?.startsWith('H3 分辨率') ? appliedH3Comment : '应用后解析分辨率'}
        </Typography.Text>}
        {binShape === 'HEXAGON' && <Form.Item label="尺寸语义">
          <Select<SpatialBinSizeSemantics> aria-label="六边形尺寸语义" value={binSizeSemantics}
            options={[{ value: 'HEXAGON_FLAT_TO_FLAT', label: '对边距离（推荐）' },
              { value: 'LEGACY_SIDE_LENGTH', label: '边长（兼容旧版）' }]}
            onChange={(next) => Modal.confirm({ title: '切换六边形尺寸语义？',
              content: '尺寸数值保持不变，但解释方式改变会影响格网位置和统计结果。若保持旧格网，边长转换为对边距离时需要乘以 √3。',
              okText: '确认切换', cancelText: '取消', onOk: () => updateConfiguration('binSizeSemantics', next) })} />
        </Form.Item>}
        <Form.Item
          name="includeEmptyBins"
          valuePropName="checked"
          label={<span className="canvas-inspector-field-label">输出空格网<ContextHelp
            ariaLabel="输出空格网风险说明"
            content={binShape === 'H3' ? 'H3 当前仅输出有点的格网；尚未提供球面范围内补齐空格网。切换形状保留原设置，请关闭此项。' : '按所选业务范围或来源索引包络补齐格网；业务范围没有点时也可生成空格网。启用时间切片时只使用实际观测窗口，不凭空补时间。结果可能显著增大。'}
          /></span>}
        >
          <Switch />
        </Form.Item>
        {includeEmptyBins && (
          <Typography.Text type={binShape === 'H3' ? 'danger' : 'warning'} className="canvas-field-inline-warning">
            {binShape === 'H3' ? 'H3 暂不支持空格网，请关闭此项。' : '已启用空格网，运行结果量可能明显增大。'}
          </Typography.Text>
        )}

        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>统计项</Typography.Text><Tag>{statistics.length}</Tag></Space>
          <Button size="small" aria-label="设置格网统计项" icon={<SettingOutlined />} onClick={() => {
            const draft: SpatialBinStatistic[] = form.getFieldValue('statistics') ?? [];
            setStatisticsDraft(draft.map(item => ({ ...item })));
            setStatisticFieldDrafts(Object.fromEntries(draft.map(item => [item.statisticId, item.sourceColumnName])));
            setStatisticsOpen(true);
          }}>设置</Button>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>分组汇总</Typography.Text>
            <Typography.Text type="secondary">{groupSummary?.groupByColumnName || '未启用'}</Typography.Text>
          </Space>
          <Space size={4}>
            <Switch size="small" checked={Boolean(groupSummary)} onChange={(checked) => {
              updateConfiguration('groupSummary', checked ? groupDraft : null);
            }} />
            <Button size="small" disabled={!groupSummary} onClick={() => setGroupOpen(true)}>设置</Button>
          </Space>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>时间切片</Typography.Text>
            <Typography.Text type="secondary">
              {temporalSlicing ? temporalWindowLabel(temporalSlicing) : '未启用'}
            </Typography.Text>
          </Space>
          <Space size={4}>
            <Switch size="small" checked={Boolean(temporalSlicing)} onChange={(checked) => {
              updateConfiguration('temporalSlicing', checked ? temporalDraft : null);
            }} />
            <Button size="small" aria-label="设置时间切片" disabled={!temporalSlicing} onClick={() => {
              const values: SpatialBinAggregateConfiguration = form.getFieldsValue(true);
              setTemporalDraft(structuredClone(values.temporalSlicing ?? createSpatialTemporalSlicing()));
              setTemporalOpen(true);
            }}>设置</Button>
          </Space>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>格网结果字段</Typography.Text><Tag>2 个</Tag></Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setFieldsOpen(true)}>设置</Button>
        </div>
        <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 order_hex_bins" />
        </Form.Item>

        <Modal
          open={statisticsOpen} width={860} title="设置格网统计项" okText="保存统计草稿" cancelText="取消"
          onOk={() => { updateConfiguration('statistics', statisticsDraft); setStatisticsOpen(false); }} onCancel={() => setStatisticsOpen(false)}
        >
          <div className="canvas-spatial-modal-toolbar">
            <Space size={4}><Typography.Text type="secondary">至少保留一个点数统计</Typography.Text>
              <ContextHelp ariaLabel="格网统计说明" content="COUNT 统计点数；COUNT_FIELD 统计指定字段的非 NULL 值（空字符串计数、不去重）。ANY 取一个非 NULL 字符串样本，不保证跨重跑选择相同记录；全 NULL 时返回 NULL。STDDEV/VARIANCE 当前为样本统计，单个有效值返回 NULL。统计结果不出现在 Canvas 或日志中。" />
            </Space>
            <Button
              type="primary" size="small" aria-label="添加统计" icon={<PlusOutlined />}
              disabled={statisticsDraft.length >= CANVAS_SPATIAL_BIN_MAX_STATISTICS}
              onClick={() => setStatisticsDraft([...statisticsDraft, {
                statisticId: crypto.randomUUID(), kind: 'COUNT',
                sourceColumnName: null, outputColumnName: 'point_count',
              }])}
            >添加统计</Button>
          </div>
          <Space orientation="vertical" size={6} style={{ width: '100%' }}>
            {statisticsDraft.map((statistic, index) => (
              <div className="canvas-spatial-statistic-row" key={statistic.statisticId}>
                <Select aria-label={`统计类型 ${index + 1}`} value={statistic.kind} options={statisticKinds}
                  onChange={(kind: SpatialBinStatisticKind) => {
                    if (statistic.kind !== 'COUNT') setStatisticFieldDrafts({ ...statisticFieldDrafts, [statistic.statisticId]: statistic.sourceColumnName });
                    updateStatistic(index, { ...statistic, kind, sourceColumnName: kind === 'COUNT' ? null
                      : statistic.kind === 'COUNT' ? statisticFieldDrafts[statistic.statisticId] ?? null : statistic.sourceColumnName });
                  }} />
                <Select
                  showSearch optionFilterProp="label" allowClear disabled={statistic.kind === 'COUNT'}
                  aria-label={`统计来源字段 ${index + 1}`}
                  status={statistic.kind !== 'COUNT' && (!statistic.sourceColumnName || Boolean(validation) && !columns.some(c => c.name === statistic.sourceColumnName && (statistic.kind !== 'ANY' || c.fieldType === 'STRING'))) ? 'error' : undefined}
                  value={statistic.sourceColumnName}
                  options={spatialColumnOptions(
                    columns, statistic.sourceColumnName ?? '',
                    (column) => statistic.kind === 'ANY' ? column.fieldType === 'STRING' : statistic.kind === 'COUNT_FIELD' || column.fieldType !== 'GEOMETRY',
                  )}
                  onChange={(sourceColumnName) => updateStatistic(index, {
                    ...statistic, sourceColumnName: sourceColumnName ?? null,
                  })}
                  placeholder={statistic.kind === 'COUNT' ? '无需字段' : '统计字段'}
                />
                <Input aria-label={`统计输出字段 ${index + 1}`} value={statistic.outputColumnName} placeholder="输出字段"
                  status={!statistic.outputColumnName.trim() || statisticsDraft.some((item, other) => other !== index && item.outputColumnName.toLowerCase() === statistic.outputColumnName.toLowerCase()) ? 'error' : undefined}
                  onChange={(event) => updateStatistic(index, {
                    ...statistic, outputColumnName: event.target.value,
                  })} />
                <Space size={0}>
                  <Button type="text" size="small" aria-label={`上移统计 ${index + 1}`} icon={<UpOutlined />} disabled={index === 0}
                    onClick={() => moveStatistic(index, index - 1)} />
                  <Button type="text" size="small" aria-label={`下移统计 ${index + 1}`} icon={<DownOutlined />}
                    disabled={index === statisticsDraft.length - 1}
                    onClick={() => moveStatistic(index, index + 1)} />
                  <Button type="text" danger size="small" aria-label={`删除统计 ${index + 1}`} icon={<DeleteOutlined />}
                    onClick={() => Modal.confirm({ title: `删除统计 ${statistic.outputColumnName || index + 1}？`,
                      content: '删除后该字段不再输出，使用它的下游配置可能失效。', okText: '删除', cancelText: '取消',
                      onOk: () => setStatisticsDraft(statisticsDraft.filter((_, itemIndex) => itemIndex !== index)),
                    })} />
                </Space>
              </div>
            ))}
          </Space>
        </Modal>
        {planarGridOpen && <PlanarGridModal initialValue={planarGrid} onCancel={() => setPlanarGridOpen(false)}
          onSave={value => { updateConfiguration('planarGrid', value); setPlanarGridOpen(false); }} />}
        <Modal open={groupOpen} width={560} title="设置格网分组汇总" okText="保存草稿"
          onOk={() => {
            updateConfiguration('groupSummary', groupDraft);
            setGroupOpen(false);
          }} onCancel={() => setGroupOpen(false)}>
          <SpatialGroupSummaryEditor value={groupDraft} columns={columns} onChange={setGroupDraft} />
        </Modal>
        <Modal open={temporalOpen} width={620} title="设置格网时间切片" okText="保存草稿"
          onOk={() => {
            updateConfiguration('temporalSlicing', temporalDraft);
            setTemporalOpen(false);
          }} cancelText="取消" onCancel={() => {
            const values: SpatialBinAggregateConfiguration = form.getFieldsValue(true);
            setTemporalDraft(structuredClone(values.temporalSlicing ?? createSpatialTemporalSlicing()));
            setTemporalOpen(false);
          }}>
          <SpatialTemporalSlicingEditor value={temporalDraft} columns={columns} onChange={setTemporalDraft} />
        </Modal>
        <Modal open={fieldsOpen} width={560} title="设置格网结果字段" okText="完成" cancelText="关闭"
          onOk={() => setFieldsOpen(false)} onCancel={() => setFieldsOpen(false)}>
          <div className="canvas-spatial-pair-grid">
            <Form.Item name="binIdColumnName" label="格网 ID" rules={[{ required: true, whitespace: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="binGeometryColumnName" label="格网 Geometry" rules={[{ required: true, whitespace: true }]}>
              <Input />
            </Form.Item>
          </div>
        </Modal>
      </Form>
    </Space>
  );
};

export default SpatialBinAggregateInspector;
