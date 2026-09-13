import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
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
import { useImperativeHandle, useRef, useState } from 'react';
import {
  CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type SnapTracksConfiguration,
  type SnapTracksDirectionMatching,
  type SnapTracksLineField,
  type TrackBoundaryConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions } from '../spatialUnits';
import { TrackBoundaryEditor } from '../trackShared';

const fingerprint = (value: SnapTracksConfiguration) => JSON.stringify(value);
const scalarColumn = (column: CanvasColumnSchema) => column.fieldType !== 'GEOMETRY';
const timestampColumn = (column: CanvasColumnSchema) => column.fieldType === 'TIMESTAMP';
const pointGeometry = (column: CanvasColumnSchema) => column.fieldType === 'GEOMETRY'
  && column.geometry?.kind === 'POINT' && column.geometry.dimension === 'XY';
const lineGeometry = (column: CanvasColumnSchema) => column.fieldType === 'GEOMETRY'
  && column.geometry?.kind === 'LINESTRING' && column.geometry.dimension === 'XY';

const multipleColumnOptions = (columns: CanvasColumnSchema[], selected: string[]) => {
  const candidates = columns.filter(scalarColumn);
  const names = new Set(candidates.map(column => column.name));
  return [
    ...selected.filter(name => !names.has(name)).map(name => ({
      value: name, label: `${name}（已失效）`, disabled: true,
    })),
    ...candidates.map(column => ({
      value: column.name, label: `${column.name} · ${column.fieldType}`,
    })),
  ];
};

const preferred = (
  columns: CanvasColumnSchema[],
  patterns: RegExp[],
) => columns.find(column => scalarColumn(column)
  && patterns.some(pattern => pattern.test(column.name)))?.name ?? '';

const defaultDirection = (): SnapTracksDirectionMatching => ({
  directionColumnName: '',
  forwardValue: 'F',
  backwardValue: 'B',
  bothValue: 'A',
  noneValue: 'N',
});

const SnapTracksInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SnapTracks>) => {
  const [form] = Form.useForm<SnapTracksConfiguration>();
  const [boundaries, setBoundaries] = useState<TrackBoundaryConfiguration>(
    () => structuredClone(node.configuration.boundaries),
  );
  const [directionMatching, setDirectionMatching] = useState<SnapTracksDirectionMatching | null>(
    () => structuredClone(node.configuration.directionMatching),
  );
  const [directionDraft, setDirectionDraft] = useState<SnapTracksDirectionMatching>(
    () => structuredClone(node.configuration.directionMatching ?? defaultDirection()),
  );
  const lastDirection = useRef<SnapTracksDirectionMatching>(
    structuredClone(node.configuration.directionMatching ?? defaultDirection()),
  );
  const [lineFields, setLineFields] = useState<SnapTracksLineField[]>(
    () => structuredClone(node.configuration.lineFields),
  );
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const [directionOpen, setDirectionOpen] = useState(false);
  const [lineFieldsOpen, setLineFieldsOpen] = useState(false);
  const [resultFieldsOpen, setResultFieldsOpen] = useState(false);
  const tables = validation?.inputTables ?? [];
  const pointTableName = Form.useWatch('pointTableName', form) ?? node.configuration.pointTableName;
  const lineTableName = Form.useWatch('lineTableName', form) ?? node.configuration.lineTableName;
  const distanceMethod = Form.useWatch('distanceMethod', form) ?? node.configuration.distanceMethod;
  const trackIdColumns = Form.useWatch('trackIdColumns', { form, preserve: true }) ?? [];
  const orderByColumns = Form.useWatch('orderByColumns', { form, preserve: true }) ?? [];
  const pointTable = tables.find(table => table.name === pointTableName);
  const lineTable = tables.find(table => table.name === lineTableName);
  const pointColumns = pointTable?.columns ?? [];
  const lineColumns = lineTable?.columns ?? [];

  const normalize = (
    value: SnapTracksConfiguration,
    nextBoundaries = boundaries,
    nextDirection = directionMatching,
    nextLineFields = lineFields,
  ): SnapTracksConfiguration => ({
    pointTableName: value.pointTableName?.trim() ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName?.trim() ?? '',
    trackIdColumns: (value.trackIdColumns ?? []).map(name => name.trim()),
    timeColumnName: value.timeColumnName?.trim() ?? '',
    orderByColumns: (value.orderByColumns ?? []).map(name => name.trim()),
    lineTableName: value.lineTableName?.trim() ?? '',
    lineGeometryColumnName: value.lineGeometryColumnName?.trim() ?? '',
    lineIdColumnName: value.lineIdColumnName?.trim() ?? '',
    fromNodeColumnName: value.fromNodeColumnName?.trim() ?? '',
    toNodeColumnName: value.toNodeColumnName?.trim() ?? '',
    searchDistance: value.searchDistance ?? null,
    searchDistanceUnit: value.searchDistanceUnit ?? null,
    distanceMethod: value.distanceMethod ?? null,
    boundaries: structuredClone(nextBoundaries),
    directionMatching: nextDirection ? structuredClone(nextDirection) : null,
    lineFields: nextLineFields.map(field => ({
      sourceColumnName: field.sourceColumnName.trim(),
      outputColumnName: field.outputColumnName.trim(),
    })),
    outputMode: value.outputMode ?? null,
    outputTableName: value.outputTableName?.trim() ?? '',
    snappedGeometryColumnName: value.snappedGeometryColumnName?.trim() ?? '',
    matchedLineIdColumnName: value.matchedLineIdColumnName?.trim() ?? '',
    matchStatusColumnName: value.matchStatusColumnName?.trim() ?? '',
    originalXColumnName: value.originalXColumnName?.trim() ?? '',
    originalYColumnName: value.originalYColumnName?.trim() ?? '',
    matchXColumnName: value.matchXColumnName?.trim() ?? '',
    matchYColumnName: value.matchYColumnName?.trim() ?? '',
    matchDistanceColumnName: value.matchDistanceColumnName?.trim() ?? '',
  });
  const markDirty = (
    value = form.getFieldsValue(true),
    nextBoundaries = boundaries,
    nextDirection = directionMatching,
    nextLineFields = lineFields,
  ) => onDirtyChange(fingerprint(normalize(
    value, nextBoundaries, nextDirection, nextLineFields,
  )) !== fingerprint(normalize(
    node.configuration,
    node.configuration.boundaries,
    node.configuration.directionMatching,
    node.configuration.lineFields,
  )));
  const submit = (value: SnapTracksConfiguration) => {
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

  const choosePointTable = (name: string) => {
    form.setFieldValue('pointTableName', name);
    const table = tables.find(item => item.name === name);
    if (!form.getFieldValue('pointGeometryColumnName')) {
      form.setFieldValue('pointGeometryColumnName', table?.columns.find(pointGeometry)?.name ?? '');
    }
    if (!form.getFieldValue('timeColumnName')) {
      form.setFieldValue('timeColumnName', table?.columns.find(timestampColumn)?.name ?? '');
    }
    if (!form.getFieldValue('outputTableName')) {
      form.setFieldValue('outputTableName', name ? `${name}_snapped` : '');
    }
    markDirty(form.getFieldsValue(true));
  };
  const chooseLineTable = (name: string) => {
    form.setFieldValue('lineTableName', name);
    const columns = tables.find(item => item.name === name)?.columns ?? [];
    if (!form.getFieldValue('lineGeometryColumnName')) {
      form.setFieldValue('lineGeometryColumnName', columns.find(lineGeometry)?.name ?? '');
    }
    if (!form.getFieldValue('lineIdColumnName')) {
      form.setFieldValue('lineIdColumnName', preferred(columns, [/^line_?id$/i, /^road_?id$/i, /^id$/i]));
    }
    if (!form.getFieldValue('fromNodeColumnName')) {
      form.setFieldValue('fromNodeColumnName', preferred(columns, [/^from_?node/i, /^from_?id$/i]));
    }
    if (!form.getFieldValue('toNodeColumnName')) {
      form.setFieldValue('toNodeColumnName', preferred(columns, [/^to_?node/i, /^to_?id$/i]));
    }
    markDirty(form.getFieldsValue(true));
  };
  const updateBoundaries = (next: TrackBoundaryConfiguration) => {
    setBoundaries(next);
    markDirty(form.getFieldsValue(true), next);
  };
  const toggleDirection = (enabled: boolean) => {
    const next = enabled ? structuredClone(lastDirection.current) : null;
    if (directionMatching) lastDirection.current = structuredClone(directionMatching);
    setDirectionMatching(next);
    if (next) setDirectionDraft(structuredClone(next));
    markDirty(form.getFieldsValue(true), boundaries, next);
  };
  const saveDirection = () => {
    const next = structuredClone(directionDraft);
    lastDirection.current = next;
    setDirectionMatching(next);
    setDirectionOpen(false);
    markDirty(form.getFieldsValue(true), boundaries, next);
  };
  const updateLineFields = (next: SnapTracksLineField[]) => {
    setLineFields(next);
    markDirty(form.getFieldsValue(true), boundaries, directionMatching, next);
  };
  const moveLineField = (from: number, to: number) => {
    const next = [...lineFields];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    updateLineFields(next);
  };
  const boundaryCount = [
    boundaries.maximumTimeGap,
    boundaries.maximumDistanceGap,
    boundaries.fixedTimeBoundary,
  ].filter(value => value != null).length;
  const excludedLineFields = new Set([
    form.getFieldValue('lineGeometryColumnName'),
    form.getFieldValue('lineIdColumnName'),
    form.getFieldValue('fromNodeColumnName'),
    form.getFieldValue('toNodeColumnName'),
  ]);
  const projectableLineColumns = lineColumns.filter(column => scalarColumn(column)
    && !excludedLineFields.has(column.name));

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation}
      unavailableMessage={validationUnavailableMessage} />
    <Form<SnapTracksConfiguration> form={form} layout="vertical" autoComplete="off"
      initialValues={normalize(node.configuration)}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}>
      <Typography.Text strong>轨迹点</Typography.Text>
      <Form.Item name="pointTableName" label="点表" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" disabled={!validation}
          placeholder="选择带即时时间的 Point 表"
          options={spatialTableOptions(tables, pointTableName)} onChange={choosePointTable} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="pointGeometryColumnName" label="Point Geometry"
          rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(pointColumns,
              form.getFieldValue('pointGeometryColumnName') ?? '', pointGeometry)} />
        </Form.Item>
        <Form.Item name="timeColumnName" label="观测时间" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(pointColumns,
              form.getFieldValue('timeColumnName') ?? '', timestampColumn)} />
        </Form.Item>
      </div>
      <Form.Item name="trackIdColumns" label={<span className="canvas-inspector-field-label">
        轨迹标识<ContextHelp ariaLabel="吸附轨迹标识说明"
          content="可组合选择 1～8 个字段标识一条轨迹。每条轨迹按即时时间和同时间顺序独立匹配。" />
      </span>} rules={[{ required: true, type: 'array', min: 1 }]}>
        <Select mode="multiple" maxCount={8} showSearch optionFilterProp="label"
          options={multipleColumnOptions(pointColumns, trackIdColumns)} />
      </Form.Item>
      <Form.Item name="orderByColumns" label={<span className="canvas-inspector-field-label">
        同时间顺序<ContextHelp ariaLabel="吸附轨迹同时间顺序说明"
          content="先按观测时间，再按这里的字段顺序排序；同一轨迹中完整排序键必须唯一。NULL 时间观测不参与分析。" />
      </span>}>
        <Select mode="multiple" showSearch optionFilterProp="label"
          options={multipleColumnOptions(pointColumns, orderByColumns)} />
      </Form.Item>

      <Typography.Text strong>道路网络</Typography.Text>
      <Form.Item name="lineTableName" label="线表" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" disabled={!validation}
          placeholder="选择可遍历的 LineString 网络"
          options={spatialTableOptions(tables, lineTableName)} onChange={chooseLineTable} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="lineGeometryColumnName" label="LineString Geometry"
          rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(lineColumns,
              form.getFieldValue('lineGeometryColumnName') ?? '', lineGeometry)} />
        </Form.Item>
        <Form.Item name="lineIdColumnName" label="唯一线 ID" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(lineColumns,
              form.getFieldValue('lineIdColumnName') ?? '', scalarColumn)} />
        </Form.Item>
        <Form.Item name="fromNodeColumnName" label="From Node" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(lineColumns,
              form.getFieldValue('fromNodeColumnName') ?? '', scalarColumn)} />
        </Form.Item>
        <Form.Item name="toNodeColumnName" label="To Node" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(lineColumns,
              form.getFieldValue('toNodeColumnName') ?? '', scalarColumn)} />
        </Form.Item>
      </div>
      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text strong>方向匹配</Typography.Text>
          <Tag>{directionMatching ? '已启用' : '所有线双向'}</Tag>
          <ContextHelp ariaLabel="吸附轨迹方向说明"
            content="启用后，把道路属性值映射为顺向、逆向、双向或禁行。未命中的值按禁行处理；映射值只用于执行，不会显示在 Canvas 卡片或安全摘要中。" />
        </Space>
        <Space size={6}>
          <Switch size="small" checked={directionMatching != null}
            aria-label="启用道路方向匹配" onChange={toggleDirection} />
          <Button size="small" icon={<SettingOutlined />} disabled={!directionMatching}
            aria-label="设置道路方向匹配" onClick={() => {
              setDirectionDraft(structuredClone(directionMatching ?? lastDirection.current));
              setDirectionOpen(true);
            }}>设置</Button>
        </Space>
      </div>
      <Modal open={directionOpen} width={680} title="设置道路方向匹配" okText="保存设置"
        cancelText="取消" onOk={saveDirection} onCancel={() => setDirectionOpen(false)}>
        <Space orientation="vertical" size={10} style={{ width: '100%' }}>
          <Form.Item label="方向字段" required>
            <Select showSearch optionFilterProp="label" value={directionDraft.directionColumnName}
              options={spatialColumnOptions(lineColumns, directionDraft.directionColumnName, scalarColumn)}
              onChange={directionColumnName => setDirectionDraft({ ...directionDraft, directionColumnName })} />
          </Form.Item>
          <div className="canvas-spatial-pair-grid">
            {([
              ['forwardValue', '顺向值（From → To）'],
              ['backwardValue', '逆向值（To → From）'],
              ['bothValue', '双向值'],
              ['noneValue', '禁行值'],
            ] as const).map(([name, label]) => <Form.Item key={name} label={label} required>
              <Input autoComplete="off" value={directionDraft[name]}
                onChange={event => setDirectionDraft({ ...directionDraft, [name]: event.target.value })} />
            </Form.Item>)}
          </div>
        </Space>
      </Modal>

      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text strong>输出道路属性</Typography.Text>
          <Tag>{lineFields.length} / {CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS}</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} aria-label="设置输出道路属性"
          onClick={() => setLineFieldsOpen(true)}>设置</Button>
      </div>
      <Modal open={lineFieldsOpen} width={760} title="设置输出道路属性" okText="完成"
        cancelText="关闭" onOk={() => setLineFieldsOpen(false)} onCancel={() => setLineFieldsOpen(false)}>
        <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          <div className="canvas-spatial-modal-toolbar">
            <Typography.Text type="secondary">从匹配道路投影属性，不参与路网匹配。</Typography.Text>
            <Button size="small" type="primary" icon={<PlusOutlined />}
              disabled={lineFields.length >= CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS}
              onClick={() => updateLineFields([...lineFields, {
                sourceColumnName: '', outputColumnName: '',
              }])}>添加字段</Button>
          </div>
          <div className="canvas-processor-operation-list">
            {lineFields.map((field, index) => {
              const invalid = Boolean(validation?.issues.some(issue => issue.severity === 'ERROR'
                && issue.path?.startsWith(`configuration.lineFields[${index}]`)));
              return <div className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
                key={`${index}-${field.sourceColumnName}`}>
                <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
                <Select size="small" showSearch optionFilterProp="label" value={field.sourceColumnName}
                  placeholder="道路字段" style={{ minWidth: 190, flex: 1 }}
                  options={spatialColumnOptions(projectableLineColumns, field.sourceColumnName, scalarColumn)}
                  onChange={sourceColumnName => {
                    const next = [...lineFields];
                    next[index] = {
                      ...field,
                      sourceColumnName,
                      outputColumnName: field.outputColumnName || `matched_${sourceColumnName}`,
                    };
                    updateLineFields(next);
                  }} />
                <Input size="small" autoComplete="off" value={field.outputColumnName}
                  placeholder="结果字段名" style={{ minWidth: 180, flex: 1 }}
                  onChange={event => {
                    const next = [...lineFields];
                    next[index] = { ...field, outputColumnName: event.target.value };
                    updateLineFields(next);
                  }} />
                <Space size={0}>
                  <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                    aria-label={`上移道路字段 ${index + 1}`} onClick={() => moveLineField(index, index - 1)} />
                  <Button type="text" size="small" icon={<DownOutlined />}
                    disabled={index === lineFields.length - 1}
                    aria-label={`下移道路字段 ${index + 1}`} onClick={() => moveLineField(index, index + 1)} />
                  <Button type="text" danger size="small" icon={<DeleteOutlined />}
                    aria-label={`删除道路字段 ${index + 1}`}
                    onClick={() => updateLineFields(lineFields.filter((_, itemIndex) => itemIndex !== index))} />
                </Space>
              </div>;
            })}
            {lineFields.length === 0 && <Typography.Text type="secondary">不输出额外道路属性。</Typography.Text>}
          </div>
        </Space>
      </Modal>

      <Typography.Text strong>匹配</Typography.Text>
      <Form.Item name="distanceMethod" label={<span className="canvas-inspector-field-label">
        距离方法<ContextHelp ariaLabel="吸附轨迹距离方法说明"
          content="平面方法要求可换算线性单位的投影 CRS；测地线方法仅支持 EPSG:4326 XY。首版只连接同一条线或共享节点的直接相邻线，不跨越无观测的多条中间道路。" />
      </span>} rules={[{ required: true }]}>
        <Segmented block options={[
          { value: 'PLANAR', label: '平面' },
          { value: 'GEODESIC', label: '测地线' },
        ]} />
      </Form.Item>
      <Form.Item label="搜索距离" required>
        <Space.Compact block>
          <Form.Item name="searchDistance" noStyle rules={[{ required: true }]}>
            <InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} />
          </Form.Item>
          <Form.Item name="searchDistanceUnit" noStyle rules={[{ required: true }]}>
            <Select style={{ width: '48%' }} options={spatialDistanceUnitOptions.filter(option => (
              distanceMethod !== 'GEODESIC' || option.value !== 'SOURCE_CRS_UNIT'
            ))} />
          </Form.Item>
        </Space.Compact>
      </Form.Item>
      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text strong>轨迹切分</Typography.Text>
          <Tag>{boundaryCount === 0 ? '不限' : `${boundaryCount} 项`}</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} aria-label="设置吸附轨迹边界"
          onClick={() => setBoundariesOpen(true)}>设置</Button>
      </div>
      <Modal open={boundariesOpen} width={680} title="设置吸附轨迹边界" okText="完成"
        cancelText="关闭" onOk={() => setBoundariesOpen(false)} onCancel={() => setBoundariesOpen(false)}>
        <TrackBoundaryEditor value={boundaries} onChange={updateBoundaries} />
      </Modal>
      <Form.Item name="outputMode" label={<span className="canvas-inspector-field-label">
        输出范围<ContextHelp ariaLabel="吸附轨迹输出范围说明"
          content="全部观测会保留未匹配点并标记 U；仅匹配观测只输出标记 M 的点。单观测轨迹保守标记为未匹配。" />
      </span>} rules={[{ required: true }]}>
        <Segmented block options={[
          { value: 'ALL_FEATURES', label: '全部观测' },
          { value: 'MATCHED_FEATURES', label: '仅匹配观测' },
        ]} />
      </Form.Item>

      <Typography.Text strong>结果</Typography.Text>
      <Form.Item name="outputTableName" label="输出表名"
        rules={[{ required: true, whitespace: true }]}>
        <Input autoComplete="off" />
      </Form.Item>
      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text strong>匹配诊断字段</Typography.Text>
          <Tag>8 项</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} aria-label="设置吸附结果字段"
          onClick={() => setResultFieldsOpen(true)}>设置</Button>
      </div>
      <Modal open={resultFieldsOpen} width={680} title="设置吸附结果字段" okText="完成"
        cancelText="关闭" onOk={() => setResultFieldsOpen(false)} onCancel={() => setResultFieldsOpen(false)}>
        <div className="canvas-spatial-pair-grid">
          {([
            ['snappedGeometryColumnName', '吸附 Point'],
            ['matchedLineIdColumnName', '匹配线 ID'],
            ['matchStatusColumnName', '匹配状态 M/U'],
            ['originalXColumnName', '原始 X'],
            ['originalYColumnName', '原始 Y'],
            ['matchXColumnName', '匹配 X'],
            ['matchYColumnName', '匹配 Y'],
            ['matchDistanceColumnName', '匹配距离（米）'],
          ] as const).map(([name, label]) => <Form.Item key={name} name={name} label={label}
            rules={[{ required: true, whitespace: true }]}>
            <Input autoComplete="off" />
          </Form.Item>)}
        </div>
      </Modal>
    </Form>
  </Space>;
};

export default SnapTracksInspector;
