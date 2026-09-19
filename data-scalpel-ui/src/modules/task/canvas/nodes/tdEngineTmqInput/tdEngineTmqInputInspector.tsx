import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { InfoCircleOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Form, Input, InputNumber, Modal, Select, Space, Spin, Table, Tag, Tooltip } from 'antd';
import type { Ref } from 'react';
import { useImperativeHandle, useMemo, useState } from 'react';
import {
  buildDataSourceSearch,
  fetchTdEngineTmqTopic,
  fetchTdEngineTmqTopics,
  useDataSource,
  useDataSources,
  useDataSourceTypes,
  type DataSource,
  type TdEngineTmqTopic,
} from '../../../../datasource';
import { configurationFingerprint, focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';
import { CanvasNodeType, type CanvasNodeByType, type CanvasNodeConfigurationUpdate, type CanvasNodeValidationResult, type TdEngineTmqInputConfiguration } from '../../canvasTypes';

interface Props {
  node: CanvasNodeByType<typeof CanvasNodeType.TdEngineTmqInput>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

type FormValues = TdEngineTmqInputConfiguration;

const available = (source: DataSource, tmqTypes: ReadonlySet<DataSource['type']>) => source.enabled
  && source.type === 'TDENGINE_WEBSOCKET'
  && source.purposes.includes('SOURCE')
  && tmqTypes.has(source.type);

export const TdEngineTmqInputInspector = ({ node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef }: Props) => {
  const [form] = Form.useForm<FormValues>();
  const [dataSourceOpen, setDataSourceOpen] = useState(false);
  const [topicOpen, setTopicOpen] = useState(false);
  const [fieldModal, setFieldModal] = useState(false);
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const topicName = Form.useWatch('topicName', form) ?? '';
  const eventTimeColumn = Form.useWatch('eventTimeColumn', form);
  const watermarkDelaySeconds = Form.useWatch('watermarkDelaySeconds', form);
  const selectedSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceTypesQuery = useDataSourceTypes();
  const tmqTypes = useMemo(() => new Set(
    (dataSourceTypesQuery.data ?? [])
      .filter((definition) => definition.capabilities.includes('TMQ_SUBSCRIBE'))
      .map((definition) => definition.id),
  ), [dataSourceTypesQuery.data]);
  const dataSourcesQuery = useDataSources(useMemo(() => ({
    search: buildDataSourceSearch({ purpose: 'SOURCE', enabled: true }), page: 0, size: 50, sort: 'name',
  }), []), dataSourceOpen);
  const sources = [...new Map((selectedSourceQuery.data
    ? [selectedSourceQuery.data, ...(dataSourcesQuery.data?.content ?? [])]
    : dataSourcesQuery.data?.content ?? [])
    .filter((source) => available(source, tmqTypes))
    .map((source) => [source.id, source])).values()];
  const topicsQuery = useQuery({
    queryKey: ['data-sources', dataSourceId, 'tdengine-tmq-topics'],
    queryFn: () => fetchTdEngineTmqTopics(dataSourceId),
    enabled: topicOpen && Boolean(dataSourceId),
    staleTime: 30_000,
  });
  const detailQuery = useQuery({
    queryKey: ['data-sources', dataSourceId, 'tdengine-tmq-topic', topicName],
    queryFn: () => fetchTdEngineTmqTopic(dataSourceId, topicName),
    enabled: Boolean(dataSourceId && topicName), staleTime: 30_000,
  });
  const toConfiguration = (values: FormValues): TdEngineTmqInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '', topicName: values.topicName ?? '',
    catalogName: values.catalogName ?? '', supertableName: values.supertableName ?? '',
    topicDefinitionFingerprint: values.topicDefinitionFingerprint ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    startingOffsets: values.startingOffsets ?? 'EARLIEST',
    maxOffsetsPerVGroupPerTrigger: values.maxOffsetsPerVGroupPerTrigger ?? 10_000,
    triggerIntervalSeconds: values.triggerIntervalSeconds ?? 10,
    eventTimeColumn: values.eventTimeColumn?.trim() || null,
    watermarkDelaySeconds: values.watermarkDelaySeconds ?? null,
  });
  const submit = (values: FormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };
  useImperativeHandle(inspectorRef, () => ({ apply: async () => {
    try { void form.validateFields().catch(() => undefined);
          submit(form.getFieldsValue(true)); return true; }
    catch (error) { focusFirstInvalidField(form, error); return false; }
  } }));
  const topics = topicsQuery.data ?? [];
  const topicOptions = topicName && !topics.some((topic) => topic.topicName === topicName)
    ? [{ topicName, supported: true } as TdEngineTmqTopic, ...topics] : topics;
  const timestampColumns = (detailQuery.data?.columns ?? [])
    .filter((column) => column.platformTypeDefinition?.type === 'TIMESTAMP');
  const eventTimeOptions = eventTimeColumn
    && !timestampColumns.some((column) => column.name === eventTimeColumn)
    ? [{ value: eventTimeColumn, label: `${eventTimeColumn}（当前不可用）`, disabled: true },
      ...timestampColumns.map((column) => ({ value: column.name, label: column.name }))]
    : timestampColumns.map((column) => ({ value: column.name, label: column.name }));

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    {(validationUnavailableMessage || validation?.issues.length) ? <Alert type="warning" showIcon message={validationUnavailableMessage ?? validation?.issues[0]?.message} /> : null}
    <Form<FormValues> autoComplete="off" form={form} layout="vertical" initialValues={node.configuration}
      onFinish={submit} onValuesChange={(_, values) => onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration))}>
      <Form.Item name="dataSourceId" label="TDengine WebSocket 数据源" rules={[{ required: true }]}>
        <Select showSearch open={dataSourceOpen} onOpenChange={setDataSourceOpen} filterOption={(input, option) => String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
          loading={dataSourcesQuery.isFetching || dataSourceTypesQuery.isFetching} placeholder="选择具备 TMQ 能力的来源" options={sources.map((source) => ({ value: source.id, label: source.name }))}
          notFoundContent={dataSourcesQuery.isFetching || dataSourceTypesQuery.isFetching ? <Spin size="small" /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 TDengine WebSocket 数据源" />}
          onChange={() => form.setFieldsValue({ topicName: '', catalogName: '', supertableName: '', topicDefinitionFingerprint: '' })} />
      </Form.Item>
      <Form.Item name="topicName" label="TMQ Topic" rules={[{ required: true }]}>
        <Select showSearch open={topicOpen} onOpenChange={setTopicOpen} disabled={!dataSourceId} loading={topicsQuery.isFetching} placeholder="选择完整超级表 Topic"
          options={topicOptions.map((topic) => ({ value: topic.topicName, label: topic.topicName, disabled: !topic.supported, title: topic.unsupportedReason ?? undefined }))}
          optionRender={(option) => { const topic = topicOptions.find((candidate) => candidate.topicName === option.value); return <Space><code>{option.label}</code>{topic && !topic.supported && <Tooltip title={topic.unsupportedReason}><Tag>不支持</Tag></Tooltip>}</Space>; }}
          onChange={(value) => { const topic = topics.find((candidate) => candidate.topicName === value); if (topic) form.setFieldsValue({ catalogName: topic.databaseName ?? '', supertableName: topic.supertableName ?? '', topicDefinitionFingerprint: topic.definitionFingerprint }); }} />
      </Form.Item>
      {detailQuery.data && <Button type="link" icon={<InfoCircleOutlined />} onClick={() => setFieldModal(true)} style={{ paddingInline: 0 }}>查看 {detailQuery.data.columns.length} 个超级表字段</Button>}
      <Form.Item name="catalogName" hidden><Input /></Form.Item><Form.Item name="supertableName" hidden><Input /></Form.Item><Form.Item name="topicDefinitionFingerprint" hidden><Input /></Form.Item>
      <Form.Item name="outputTableName" label="输出逻辑表名" rules={[{ required: true, whitespace: true }, { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' }]}><Input placeholder="例如 meter_events" /></Form.Item>
      <Form.Item name="startingOffsets" label="首次启动位置" rules={[{ required: true }]}><Select options={[{ value: 'EARLIEST', label: 'EARLIEST · 从 WAL 最早位置开始' }, { value: 'LATEST', label: 'LATEST · 从最新位置开始' }]} /></Form.Item>
      <Form.Item name="maxOffsetsPerVGroupPerTrigger" label="每 VGroup 每批最大 Offset 跨度" rules={[{ required: true }]}><InputNumber min={1} max={1_000_000} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="triggerIntervalSeconds" label="微批间隔（秒）" rules={[{ required: true }]}><InputNumber min={1} max={300} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item
        name="eventTimeColumn"
        label={<Space size={4}>事件时间字段（可选）<Tooltip title="只从 TIMESTAMP 字段中选择。配置后会在来源处建立 Watermark，供后续窗口和有状态处理使用。"><InfoCircleOutlined /></Tooltip></Space>}
      >
        <Select
          allowClear
          showSearch
          loading={detailQuery.isFetching}
          disabled={!topicName}
          placeholder="不指定事件时间"
          options={eventTimeOptions}
        />
      </Form.Item>
      {(eventTimeColumn || watermarkDelaySeconds != null) ? <Form.Item
        name="watermarkDelaySeconds"
        label="Watermark 延迟（秒）"
        rules={[{
          validator: (_, value) => {
            if (eventTimeColumn && value == null) {
              return Promise.reject(new Error('配置事件时间后必须填写 Watermark 延迟'));
            }
            if (!eventTimeColumn && value != null) {
              return Promise.reject(new Error('配置 Watermark 延迟后必须选择事件时间字段'));
            }
            return Promise.resolve();
          },
        }]}
      >
        <InputNumber min={1} max={2_592_000} precision={0} style={{ width: '100%' }} />
      </Form.Item> : null}
      <Alert type="info" showIcon message="首次启动位置仅在没有 Spark Checkpoint 时生效；Offset 跨度不等于行数。" />
    </Form>
    <Modal rootClassName="business-overlay business-modal-overlay" title={`超级表字段 · ${detailQuery.data?.supertableName ?? ''}`} open={fieldModal} footer={null} width={760} onCancel={() => setFieldModal(false)}>
      <Table size="small" pagination={false} rowKey="name" dataSource={detailQuery.data?.columns ?? []} columns={[{ title: '字段', dataIndex: 'name', render: (value: string) => <code>{value}</code> }, { title: '角色', dataIndex: 'role' }, { title: 'TDengine 类型', dataIndex: 'nativeType' }, { title: '平台类型', render: (_, column) => column.platformTypeDefinition?.type ?? '—' }]} />
    </Modal>
  </Space>;
};
