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
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type TraceProximityEntityOfInterest,
  type TraceProximityEventsConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';
import { spatialDistanceUnitOptions } from '../spatialUnits';

const fingerprint = (value: TraceProximityEventsConfiguration) => JSON.stringify(value);
const temporalUnitOptions = [
  ['MILLISECONDS', '毫秒'], ['SECONDS', '秒'], ['MINUTES', '分钟'],
  ['HOURS', '小时'], ['DAYS', '天'], ['WEEKS', '周'],
  ['MONTHS', '月（日历）'], ['YEARS', '年（日历）'],
].map(([value, label]) => ({ value, label }));

const timestampColumn = (column: CanvasColumnSchema) => column.fieldType === 'TIMESTAMP';
const stringColumn = (column: CanvasColumnSchema) => column.fieldType === 'STRING';
const attributeColumn = (column: CanvasColumnSchema) => column.fieldType !== 'GEOMETRY';

const localDateTime = (epochMillis: number | null) => {
  if (epochMillis == null) return '';
  const value = new Date(epochMillis);
  if (Number.isNaN(value.getTime())) return '';
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 19);
};

const parseLocalDateTime = (value: string): number | null => {
  if (!value) return null;
  const parsed = new Date(value).getTime();
  return Number.isSafeInteger(parsed) ? parsed : null;
};

const multipleColumnOptions = (columns: CanvasColumnSchema[], selected: string[]) => {
  const names = new Set(columns.map(column => column.name));
  return [
    ...selected.filter(name => !names.has(name)).map(name => ({
      value: name, label: `${name}（已失效）`, disabled: true,
    })),
    ...columns.filter(attributeColumn).map(column => ({
      value: column.name, label: `${column.name} · ${column.fieldType}`,
    })),
  ];
};

const TraceProximityEventsInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.TraceProximityEvents>) => {
  const [form] = Form.useForm<TraceProximityEventsConfiguration>();
  const [entities, setEntities] = useState<TraceProximityEntityOfInterest[]>(
    () => structuredClone(node.configuration.entitiesOfInterest),
  );
  const [resultFieldsOpen, setResultFieldsOpen] = useState(false);
  const tables = validation?.inputTables ?? [];
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? node.configuration.sourceTableName;
  const interestSource = Form.useWatch('interestSource', form) ?? node.configuration.interestSource;
  const interestTableName = Form.useWatch('entitiesOfInterestTableName', form)
    ?? node.configuration.entitiesOfInterestTableName;
  const distanceMethod = Form.useWatch('distanceMethod', form) ?? node.configuration.distanceMethod;
  const includeTracks = Form.useWatch('includeTracks', form) ?? node.configuration.includeTracks;
  const attributeMatchColumns = Form.useWatch('attributeMatchColumns', { form, preserve: true }) ?? [];
  const sourceTable = tables.find(table => table.name === sourceTableName);
  const interestTable = tables.find(table => table.name === interestTableName);
  const sourceColumns = sourceTable?.columns ?? [];
  const interestColumns = interestTable?.columns ?? [];

  const normalize = (
    value: TraceProximityEventsConfiguration,
    interestEntities = entities,
  ): TraceProximityEventsConfiguration => ({
    sourceTableName: value.sourceTableName?.trim() ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName?.trim() ?? '',
    entityIdColumnName: value.entityIdColumnName?.trim() ?? '',
    timeColumnName: value.timeColumnName?.trim() ?? '',
    distanceMethod: value.distanceMethod ?? null,
    spatialSearchDistance: value.spatialSearchDistance ?? null,
    spatialSearchDistanceUnit: value.spatialSearchDistanceUnit ?? null,
    temporalSearchDistance: value.temporalSearchDistance ?? null,
    temporalSearchDistanceUnit: value.temporalSearchDistanceUnit ?? null,
    interestSource: value.interestSource ?? null,
    entitiesOfInterest: interestEntities.map(entity => ({
      entityId: entity.entityId,
      startEpochMillis: entity.startEpochMillis ?? null,
    })),
    entitiesOfInterestTableName: value.entitiesOfInterestTableName?.trim() ?? '',
    interestEntityIdColumnName: value.interestEntityIdColumnName?.trim() ?? '',
    interestStartTimeColumnName: value.interestStartTimeColumnName?.trim() || null,
    maxTraceDepth: value.maxTraceDepth ?? null,
    attributeMatchColumns: (value.attributeMatchColumns ?? []).map(column => column.trim()),
    includeTracks: value.includeTracks === true,
    outputTableName: value.outputTableName?.trim() ?? '',
    tracksOutputTableName: value.tracksOutputTableName?.trim() ?? '',
    fromEntityIdColumnName: value.fromEntityIdColumnName?.trim() ?? '',
    toEntityIdColumnName: value.toEntityIdColumnName?.trim() ?? '',
    depthColumnName: value.depthColumnName?.trim() ?? '',
    durationMinutesColumnName: value.durationMinutesColumnName?.trim() ?? '',
    eventTimeColumnName: value.eventTimeColumnName?.trim() ?? '',
  });
  const markDirty = (
    value = form.getFieldsValue(true),
    interestEntities = entities,
  ) => onDirtyChange(
    fingerprint(normalize(value, interestEntities)) !== fingerprint(normalize(node.configuration,
      node.configuration.entitiesOfInterest)),
  );
  const submit = (value: TraceProximityEventsConfiguration) => {
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

  const updateEntities = (next: TraceProximityEntityOfInterest[]) => {
    setEntities(next);
    markDirty(form.getFieldsValue(true), next);
  };
  const moveEntity = (from: number, to: number) => {
    const next = [...entities];
    [next[from], next[to]] = [next[to], next[from]];
    updateEntities(next);
  };
  const chooseSourceTable = (nextName: string) => {
    form.setFieldValue('sourceTableName', nextName);
    const table = tables.find(item => item.name === nextName);
    if (!form.getFieldValue('pointGeometryColumnName')) {
      const geometry = table?.columns.find(column => column.fieldType === 'GEOMETRY'
        && column.geometry?.kind === 'POINT' && column.geometry.dimension === 'XY');
      if (geometry) form.setFieldValue('pointGeometryColumnName', geometry.name);
    }
    if (!form.getFieldValue('entityIdColumnName')) {
      const entity = table?.columns.find(stringColumn);
      if (entity) form.setFieldValue('entityIdColumnName', entity.name);
    }
    if (!form.getFieldValue('timeColumnName')) {
      const time = table?.columns.find(timestampColumn);
      if (time) form.setFieldValue('timeColumnName', time.name);
    }
    if (!form.getFieldValue('outputTableName')) {
      form.setFieldValue('outputTableName', nextName ? `${nextName}_trace_events` : '');
    }
    if (!form.getFieldValue('tracksOutputTableName')) {
      form.setFieldValue('tracksOutputTableName', nextName ? `${nextName}_trace_tracks` : '');
    }
    markDirty(form.getFieldsValue(true));
  };
  const chooseInterestTable = (nextName: string) => {
    form.setFieldValue('entitiesOfInterestTableName', nextName);
    const table = tables.find(item => item.name === nextName);
    if (!form.getFieldValue('interestEntityIdColumnName')) {
      const entity = table?.columns.find(stringColumn);
      if (entity) form.setFieldValue('interestEntityIdColumnName', entity.name);
    }
    markDirty(form.getFieldsValue(true));
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation}
      unavailableMessage={validationUnavailableMessage} />
    <Form<TraceProximityEventsConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={normalize(node.configuration)}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <Typography.Text strong>观测来源</Typography.Text>
      <Form.Item name="sourceTableName" label="轨迹观测表" rules={[{ required: true }]}>
        <Select showSearch optionFilterProp="label" disabled={!validation}
          placeholder="选择带时间的 Point 表"
          options={spatialTableOptions(tables, sourceTableName)} onChange={chooseSourceTable} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="pointGeometryColumnName" label="Point Geometry"
          rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" placeholder="选择 XY Point"
            options={spatialColumnOptions(
              sourceColumns, form.getFieldValue('pointGeometryColumnName') ?? '',
              column => column.fieldType === 'GEOMETRY'
                && column.geometry?.kind === 'POINT' && column.geometry.dimension === 'XY',
            )} />
        </Form.Item>
        <Form.Item name="entityIdColumnName" label="实体 ID" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(
              sourceColumns, form.getFieldValue('entityIdColumnName') ?? '', stringColumn,
            )} />
        </Form.Item>
        <Form.Item name="timeColumnName" label="观测时间" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label"
            options={spatialColumnOptions(
              sourceColumns, form.getFieldValue('timeColumnName') ?? '', timestampColumn,
            )} />
        </Form.Item>
      </div>

      <Typography.Text strong>接触判定</Typography.Text>
      <Form.Item name="distanceMethod" label={<span className="canvas-inspector-field-label">
        距离方法<ContextHelp ariaLabel="邻近事件距离方法说明"
          content="平面距离要求投影 CRS；测地线距离仅支持 EPSG:4326 XY。空间、时间和所有同值字段必须同时满足。" />
      </span>} rules={[{ required: true }]}>
        <Segmented block options={[
          { value: 'PLANAR', label: '平面' },
          { value: 'GEODESIC', label: '测地线' },
        ]} />
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item label="空间搜索距离" required>
          <Space.Compact block>
            <Form.Item name="spatialSearchDistance" noStyle rules={[{ required: true }]}>
              <InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} />
            </Form.Item>
            <Form.Item name="spatialSearchDistanceUnit" noStyle rules={[{ required: true }]}>
              <Select style={{ width: '48%' }} options={spatialDistanceUnitOptions.filter(option => (
                distanceMethod !== 'GEODESIC' || option.value !== 'SOURCE_CRS_UNIT'
              ))} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>
        <Form.Item label="时间搜索距离" required>
          <Space.Compact block>
            <Form.Item name="temporalSearchDistance" noStyle rules={[{ required: true }]}>
              <InputNumber min={0} precision={0} style={{ width: '52%' }} />
            </Form.Item>
            <Form.Item name="temporalSearchDistanceUnit" noStyle rules={[{ required: true }]}>
              <Select style={{ width: '48%' }} options={temporalUnitOptions} />
            </Form.Item>
          </Space.Compact>
        </Form.Item>
      </div>
      <Form.Item name="attributeMatchColumns" label={<span className="canvas-inspector-field-label">
        同值属性（可选）<ContextHelp ariaLabel="邻近事件同值属性说明"
          content="例如楼层或区域。两条观测的所有已选字段都相等时才可形成接触；NULL 不与任何值匹配。最多 8 个。" />
      </span>}>
        <Select mode="multiple" maxCount={8} showSearch optionFilterProp="label"
          options={multipleColumnOptions(sourceColumns, attributeMatchColumns)} />
      </Form.Item>

      <Typography.Text strong>起始实体</Typography.Text>
      <Form.Item name="interestSource" label="定义方式" rules={[{ required: true }]}>
        <Segmented block options={[
          { value: 'ENTITY_IDS', label: '直接填写 ID' },
          { value: 'TABLE', label: '从上游表读取' },
        ]} />
      </Form.Item>
      {interestSource === 'ENTITY_IDS' ? <>
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>起始实体列表</Typography.Text>
            <Tag>{entities.length} / 256</Tag>
            <ContextHelp ariaLabel="起始实体时间说明"
              content="实体 ID 区分大小写。开始时间为空时从 1970-01-01 开始追踪。ID 值只用于执行，不会显示在 Canvas 卡片或安全摘要中。" />
          </Space>
          <Button size="small" type="primary" icon={<PlusOutlined />}
            disabled={entities.length >= 256}
            onClick={() => updateEntities([...entities, { entityId: '', startEpochMillis: null }])}>
            添加
          </Button>
        </div>
        <div className="canvas-processor-operation-list">
          {entities.map((entity, index) => {
            const invalid = Boolean(validation?.issues.some(issue => issue.severity === 'ERROR'
              && issue.path?.startsWith(`configuration.entitiesOfInterest[${index}]`)));
            return <div className={`canvas-processor-operation-row${invalid ? ' is-invalid' : ''}`}
              key={index}>
              <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
              <Input size="small" value={entity.entityId} placeholder="实体 ID"
                aria-label={`起始实体 ${index + 1} ID`}
                style={{ minWidth: 112, flex: 1 }} onChange={event => {
                  const next = [...entities];
                  next[index] = { ...entity, entityId: event.target.value };
                  updateEntities(next);
                }} />
              <Input size="small" type="datetime-local" step={1}
                aria-label={`起始实体 ${index + 1} 开始时间`}
                value={localDateTime(entity.startEpochMillis)} style={{ minWidth: 150, flex: 1.25 }}
                onChange={event => {
                  const next = [...entities];
                  next[index] = { ...entity, startEpochMillis: parseLocalDateTime(event.target.value) };
                  updateEntities(next);
                }} />
              <Space size={0}>
                <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
                  aria-label={`上移起始实体 ${index + 1}`}
                  onClick={() => moveEntity(index, index - 1)} />
                <Button type="text" size="small" icon={<DownOutlined />}
                  disabled={index === entities.length - 1}
                  aria-label={`下移起始实体 ${index + 1}`}
                  onClick={() => moveEntity(index, index + 1)} />
                <Button type="text" danger size="small" icon={<DeleteOutlined />}
                  aria-label={`删除起始实体 ${index + 1}`}
                  onClick={() => updateEntities(entities.filter((_, itemIndex) => itemIndex !== index))} />
              </Space>
            </div>;
          })}
          {entities.length === 0 && <Typography.Text type="secondary">
            至少添加一个起始实体；当前草稿仍可保存。
          </Typography.Text>}
        </div>
      </> : <>
        <Form.Item name="entitiesOfInterestTableName" label="起始实体表"
          rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            placeholder="选择另一张有界上游表"
            options={spatialTableOptions(tables, interestTableName)} onChange={chooseInterestTable} />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="interestEntityIdColumnName" label="起始实体 ID"
            rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label"
              options={spatialColumnOptions(
                interestColumns, form.getFieldValue('interestEntityIdColumnName') ?? '', stringColumn,
              )} />
          </Form.Item>
          <Form.Item name="interestStartTimeColumnName" label="起始时间（可选）">
            <Select allowClear showSearch optionFilterProp="label" placeholder="空表示从 1970-01-01 开始"
              options={spatialColumnOptions(
                interestColumns, form.getFieldValue('interestStartTimeColumnName') ?? '',
                timestampColumn,
              )} />
          </Form.Item>
        </div>
      </>}
      <Form.Item name="maxTraceDepth" label={<span className="canvas-inspector-field-label">
        最大传播深度<ContextHelp ariaLabel="最大传播深度说明"
          content="起始实体为深度 0，与其首次接触的实体为深度 1。下游实体只输出首次追踪事件。范围 1～32。" />
      </span>} rules={[{ required: true }]}>
        <InputNumber min={1} max={32} precision={0} style={{ width: '100%' }} />
      </Form.Item>

      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text strong>输出结果</Typography.Text>
          <Tag>{includeTracks ? '2 张表' : '1 张表'}</Tag>
        </Space>
        <Button size="small" icon={<SettingOutlined />} onClick={() => setResultFieldsOpen(true)}>
          设置字段
        </Button>
      </div>
      <Form.Item name="outputTableName" label="首次接触事件表"
        rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 device_trace_events" />
      </Form.Item>
      <div className="canvas-processor-section-header">
        <Space size={6}>
          <Typography.Text>输出首次接触后的轨迹</Typography.Text>
          <ContextHelp ariaLabel="后续轨迹输出说明"
            content="起始实体从配置的开始时间输出；下游实体从首次接触观测及其之后输出。每条轨迹观测追加传播深度。" />
        </Space>
        <Form.Item name="includeTracks" valuePropName="checked" noStyle>
          <Switch size="small" aria-label="输出后续轨迹" />
        </Form.Item>
      </div>
      {includeTracks && <Form.Item name="tracksOutputTableName" label="后续轨迹表"
        rules={[{ required: true, whitespace: true }]}>
        <Input placeholder="例如 device_trace_tracks" />
      </Form.Item>}

      <Modal open={resultFieldsOpen} width={680} title="设置邻近追踪结果字段"
        okText="完成" cancelText="关闭" onOk={() => setResultFieldsOpen(false)}
        onCancel={() => setResultFieldsOpen(false)}>
        <Typography.Paragraph type="secondary">
          事件表保留下游实体首次接触观测的全部原字段，并追加下列 5 个追踪字段。
        </Typography.Paragraph>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="fromEntityIdColumnName" label="上游实体 ID"
            rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="toEntityIdColumnName" label="下游实体 ID"
            rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="depthColumnName" label="传播深度"
            rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="durationMinutesColumnName" label="持续接触分钟数"
            rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
          <Form.Item name="eventTimeColumnName" label="首次接触时间"
            rules={[{ required: true, whitespace: true }]}><Input /></Form.Item>
        </div>
      </Modal>
      <Typography.Text type="secondary">
        分析只使用有界 Point 观测。Geometry、时间或实体 ID 为 NULL 的行不参与追踪。
      </Typography.Text>
    </Form>
  </Space>;
};

export default TraceProximityEventsInspector;
