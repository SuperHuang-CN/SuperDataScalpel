import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { DeleteOutlined, EyeOutlined, PlusOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons';
import { Button, Card, Checkbox, Descriptions, Form, Input, InputNumber, Select, Space, Table, Tag, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useEffect, useImperativeHandle, useMemo, useState, type Ref } from 'react';
import { useApiResource, useDataSource, useTableMetadata, type DataSource, type TableMetadata } from '../../../datasource';
import {
  dataModelStatusLabels,
  physicalTableModeLabels,
  useDataModel,
  type DataModelDetail,
  type DataModelField,
} from '../../../model';
import { ApiError } from '../../../../shared/api/http';
import {
  fileDatasetParseStatusLabels,
  useFileDatasetCanvasMetadata,
  useFileDatasets,
  useFileDatasetTables,
} from '../../../filedataset';
import { platformTypeLabel } from '../canvasSchema';
import { isSensitiveRuntimeParameterName } from '../canvasDefinitionIO';
import { jdbcWriteModeUnavailableReason } from '../jdbcDatabaseCapabilities';
import {
  type CanvasColumnSchema,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasExecutionMode,
  type CanvasNodeValidationResult,
  type JdbcColumnMapping,
  type FileDatasetInputConfiguration,
  type HttpApiInputConfiguration,
  type JdbcOutputConfiguration,
  type JoinCondition,
  type JoinConfiguration,
  type JoinOutputColumn,
  type KafkaInputConfiguration,
  type KafkaInputMetadataField,
  type KafkaInputValueFormat,
  type KafkaOutputConfiguration,
  type KafkaOutputValueFormat,
  type KafkaValueSchema,
  type ModelInputConfiguration,
  type ModelOutputConfiguration,
  type RenameConfiguration,
  type StreamJoinConfiguration,
} from '../canvasTypes';
import { CanvasJdbcDataSourceSelect, CanvasJdbcTableSelect } from './CanvasJdbcSelectors';
import { CanvasHttpApiDataSourceSelect, CanvasHttpApiResourceSelect } from './CanvasHttpApiSelectors';
import { CanvasModelSelect } from './CanvasModelSelect';
import { CanvasModelDetailModal } from './CanvasModelDetailModal';
import { CanvasInspectorFieldLabel } from './CanvasInspectorFieldLabel';
import { CanvasKafkaDataSourceSelect, CanvasKafkaTopicSelect } from './CanvasKafkaSelectors';
import { KafkaValueSchemaEditor } from './KafkaValueSchemaEditor';
import { configurationFingerprint, focusFirstInvalidField } from './CanvasInspectorUtils';
import {
  OutputFieldMappingFields,
} from './OutputFieldMappingFields';
import { orderOutputFieldMappings } from './outputFieldMappings';
import { JoinOutputColumnsEditor } from './JoinOutputColumnsEditor';
import { suggestJoinOutputColumns } from './joinOutputColumns';
import { CanvasNodeValidationIssues } from './common/CanvasNodeValidationIssues';

interface CanvasNodeInspectorProps {
  node: CanvasNodeDefinition | null;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage?: string | null;
  executionMode?: CanvasExecutionMode;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
}

export interface CanvasNodeInspectorHandle {
  apply: () => Promise<boolean>;
}

export const ValidationIssues = ({
  validation,
  unavailableMessage,
}: {
  validation: CanvasNodeValidationResult | undefined;
  unavailableMessage: string | null;
}) => <CanvasNodeValidationIssues
  validation={validation}
  unavailableMessage={unavailableMessage}
/>;

export const FieldPreview = ({ columns, loading = false }: { columns: CanvasColumnSchema[]; loading?: boolean }) => (
  <Table<CanvasColumnSchema>
    size="small"
    rowKey="name"
    pagination={false}
    loading={loading}
    scroll={{ y: 220 }}
    dataSource={columns}
    columns={[
      { title: '字段', dataIndex: 'name', ellipsis: true },
      { title: '平台类型', key: 'type', width: 125, render: (_, column) => platformTypeLabel(column) },
      { title: '可空', dataIndex: 'nullable', width: 54, render: (nullable: boolean) => nullable ? '是' : '否' },
    ]}
  />
);

const canvasColumns = (metadata: TableMetadata | undefined): CanvasColumnSchema[] => (
  metadata?.columns.flatMap((column): CanvasColumnSchema[] => column.platformTypeDefinition ? [{
    name: column.name,
    fieldType: column.platformTypeDefinition.type,
    length: column.platformTypeDefinition.length,
    precision: column.platformTypeDefinition.precision,
    scale: column.platformTypeDefinition.scale,
    nullable: column.nullable,
    defaultValue: column.defaultValue,
    autoIncrement: column.autoIncrement,
    generated: column.generated,
    comment: column.comment,
    geometry: column.platformTypeDefinition.geometry ?? null,
  }] : []) ?? []
);

const dataSourceAvailable = (
  dataSource: DataSource | undefined,
  purpose: 'SOURCE' | 'STORAGE' | 'DISTRIBUTION',
) => (
  dataSource !== undefined
  && dataSource.enabled
  && dataSource.connectionKind === 'JDBC'
  && dataSource.purposes.includes(purpose)
);

const kafkaDataSourceAvailable = (
  dataSource: DataSource | undefined,
  purpose: 'SOURCE' | 'DISTRIBUTION',
) => (
  dataSource !== undefined
  && dataSource.enabled
  && dataSource.connectionKind === 'KAFKA'
  && dataSource.purposes.includes(purpose)
);

const metadataErrorMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.message : fallback
);

const MetadataErrorAlert = ({
  error,
  fallback,
  onRetry,
}: {
  error: unknown;
  fallback: string;
  onRetry: () => void;
}) => (
  <Alert
    showIcon
    type="error"
    title={metadataErrorMessage(error, fallback)}
    action={<Button type="link" size="small" onClick={onRetry}>重试</Button>}
  />
);

interface FileDatasetInputFormValues {
  fileDatasetId: string;
  fileDatasetTableId: string;
}

export const FileDatasetInputInspector = ({
  node,
  validation,
  validationUnavailableMessage = null,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'FILE_DATASET_INPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<FileDatasetInputFormValues>();
  const selectedDatasetId = Form.useWatch('fileDatasetId', form) ?? '';
  const selectedTableId = Form.useWatch('fileDatasetTableId', form) ?? '';
  const datasetsQuery = useFileDatasets({ page: 0, size: 200, sort: 'name' });
  const tablesQuery = useFileDatasetTables(selectedDatasetId || undefined, Boolean(selectedDatasetId));
  const savedMetadataQuery = useFileDatasetCanvasMetadata(
    node.configuration.tables[0]?.fileDatasetTableId ? [node.configuration.tables[0].fileDatasetTableId] : [],
    Boolean(node.configuration.tables[0]?.fileDatasetTableId),
  );
  const selectedMetadataQuery = useFileDatasetCanvasMetadata(
    selectedTableId ? [selectedTableId] : [],
    Boolean(selectedTableId),
  );
  const savedMetadata = savedMetadataQuery.data?.tables.find(
    (table) => table.fileDatasetTableId === node.configuration.tables[0]?.fileDatasetTableId,
  );
  const selectedMetadata = selectedMetadataQuery.data?.tables.find(
    (table) => table.fileDatasetTableId === selectedTableId,
  );

  useEffect(() => {
    if (!savedMetadata || form.getFieldValue('fileDatasetId')) return;
    form.setFieldValue('fileDatasetId', savedMetadata.fileDatasetId);
  }, [form, savedMetadata]);

  const toConfiguration = (values: FileDatasetInputFormValues): FileDatasetInputConfiguration => ({
    fileDatasetId: values.fileDatasetId ?? '',
    tables: values.fileDatasetTableId ? [{ fileDatasetTableId: values.fileDatasetTableId }] : [],
  });
  const submit = (values: FileDatasetInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const datasetOptions = (datasetsQuery.data?.content ?? []).map((dataset) => ({
    value: dataset.id,
    label: `${dataset.name} · ${dataset.type}`,
  }));
  if (savedMetadata && !datasetOptions.some((option) => option.value === savedMetadata.fileDatasetId)) {
    datasetOptions.push({
      value: savedMetadata.fileDatasetId,
      label: `${savedMetadata.fileDatasetName} · ${savedMetadata.datasetType}`,
    });
  }
  const tableOptions = (tablesQuery.data?.content ?? []).map((table) => ({
    value: table.id,
    label: `${table.name} · ${table.code} · ${fileDatasetParseStatusLabels[table.parseStatus]}`,
    disabled: table.parseStatus !== 'READY' && table.parseStatus !== 'SCHEMA_READY',
  }));
  if (selectedTableId && !tableOptions.some((option) => option.value === selectedTableId)) {
    tableOptions.push({
      value: selectedTableId,
      label: selectedMetadata
        ? `${selectedMetadata.name} · ${selectedMetadata.code} · ${fileDatasetParseStatusLabels[selectedMetadata.parseStatus]}`
        : `不可用的已保存表 · ${selectedTableId}`,
      disabled: true,
    });
  }
  const selectedColumns: CanvasColumnSchema[] = [...(selectedMetadata?.fields ?? [])]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((field) => ({
      name: field.name,
      fieldType: field.platformTypeDefinition.type,
      length: field.platformTypeDefinition.length,
      precision: field.platformTypeDefinition.precision,
      scale: field.platformTypeDefinition.scale,
      nullable: field.nullable,
      defaultValue: null,
      autoIncrement: false,
      generated: false,
      comment: null,
      geometry: field.platformTypeDefinition.geometry ?? null,
    }));

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Alert
        showIcon
        type="info"
        title="文件数据集输入仅支持批处理"
        description="定义只保存逻辑表 ID；表 code 是不可修改的输出表名，字段与可执行状态由 Task Engine 校验。"
      />
      <Form<FileDatasetInputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          fileDatasetId: node.configuration.fileDatasetId || savedMetadata?.fileDatasetId || '',
          fileDatasetTableId: node.configuration.tables[0]?.fileDatasetTableId ?? '',
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="fileDatasetId" label="文件数据集" rules={[{ required: true, message: '请选择文件数据集' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            loading={datasetsQuery.isFetching || savedMetadataQuery.isFetching}
            placeholder="选择文件数据集"
            options={datasetOptions}
            onChange={() => form.setFieldValue('fileDatasetTableId', '')}
          />
        </Form.Item>
        {datasetsQuery.isError && (
          <MetadataErrorAlert
            error={datasetsQuery.error}
            fallback="读取文件数据集失败"
            onRetry={() => void datasetsQuery.refetch()}
          />
        )}
        <Form.Item
          name="fileDatasetTableId"
          label="逻辑表"
          rules={[{ required: true, message: '请选择已就绪的逻辑表' }]}
          extra="READY 和仅缺少预览能力的 SCHEMA_READY 表可以选择；已保存表失效时会保留原 ID并显示 Compiler 错误。"
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!selectedDatasetId}
            loading={tablesQuery.isFetching}
            placeholder="选择 READY 逻辑表"
            options={tableOptions}
          />
        </Form.Item>
        {tablesQuery.isError && (
          <MetadataErrorAlert
            error={tablesQuery.error}
            fallback="读取文件数据集逻辑表失败"
            onRetry={() => void tablesQuery.refetch()}
          />
        )}
        {selectedTableId && (
          <Card
            size="small"
            title={selectedMetadata
              ? `字段 · ${selectedMetadata.name} (${selectedMetadata.code})`
              : '字段 Schema'}
          >
            <FieldPreview columns={selectedColumns} loading={selectedMetadataQuery.isFetching} />
          </Card>
        )}
      </Form>
    </Space>
  );
};

interface HttpApiInputFormValues {
  dataSourceId: string;
  resourceId: string;
  outputTableName: string;
  runtimeParameters: { name: string; value: string }[];
}

export const HttpApiInputInspector = ({
  node,
  validation,
  validationUnavailableMessage = null,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'HTTP_API_INPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<HttpApiInputFormValues>();
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const resourceId = Form.useWatch('resourceId', form) ?? '';
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const resourceQuery = useApiResource(dataSourceId || undefined, resourceId || undefined, Boolean(dataSourceId && resourceId));
  const sourceAvailable = dataSourceQuery.data
    ? dataSourceQuery.data.enabled
      && dataSourceQuery.data.connectionKind === 'HTTP_API'
      && dataSourceQuery.data.purposes.includes('SOURCE')
    : dataSourceQuery.isError ? false : undefined;
  const resourceAvailable = resourceQuery.data
    ? resourceQuery.data.enabled && resourceQuery.data.dataSourceId === dataSourceId
    : resourceQuery.isError ? false : undefined;
  const columns: CanvasColumnSchema[] = resourceQuery.data?.outputFields.map((field) => ({
    name: field.name,
    fieldType: field.type.type,
    length: field.type.length,
    precision: field.type.precision,
    scale: field.type.scale,
    nullable: field.nullable,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: field.comment,
    geometry: null,
  })) ?? [];
  const toConfiguration = (values: HttpApiInputFormValues): HttpApiInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '',
    resources: values.resourceId ? [{
      resourceId: values.resourceId,
      outputTableName: values.outputTableName?.trim() ?? '',
      runtimeParameters: (values.runtimeParameters ?? []).map((parameter) => ({
        name: parameter.name?.trim() ?? '', value: parameter.value ?? '',
      })),
    }] : [],
  });
  const submit = (values: HttpApiInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };
  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true) as HttpApiInputFormValues;
        void form.validateFields().catch(() => undefined);
        const sensitiveParameterIndex = (values.runtimeParameters ?? []).findIndex(
          (parameter) => isSensitiveRuntimeParameterName(parameter.name ?? ''),
        );
        if (sensitiveParameterIndex >= 0) {
          form.setFields([{
            name: ['runtimeParameters', sensitiveParameterIndex, 'name'],
            errors: ['运行时参数不能用于密码、Token、API Key、Secret 或签名'],
          }]);
          return false;
        }
        if (values.dataSourceId && sourceAvailable !== true) {
          form.setFields([{ name: 'dataSourceId', errors: [dataSourceQuery.isFetching ? '正在读取数据源，请稍候' : 'HTTP API 数据源不存在、停用或不具有 SOURCE 用途'] }]);

        }
        if (values.resourceId && resourceAvailable !== true) {
          form.setFields([{ name: 'resourceId', errors: [resourceQuery.isFetching ? '正在读取 API 资源，请稍候' : 'API 资源不存在或已停用'] }]);

        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));
  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<HttpApiInputFormValues> autoComplete="off"
      form={form}
      layout="vertical"
      initialValues={node.configuration}
      onFinish={submit}
      onValuesChange={(_changed, values) => onDirtyChange(
        configurationFingerprint(toConfiguration(values as HttpApiInputFormValues))
          !== configurationFingerprint(node.configuration),
      )}
    >
      <Form.Item name="dataSourceId" label="HTTP API 数据源" rules={[{ required: true, message: '请选择 HTTP API 数据源' }]}>
        <CanvasHttpApiDataSourceSelect />
      </Form.Item>
      {dataSourceId && dataSourceQuery.isError && <MetadataErrorAlert error={dataSourceQuery.error} fallback="读取 API 数据源失败" onRetry={() => void dataSourceQuery.refetch()} />}
      <Form.Item name="resourceId" label="API 资源" dependencies={['dataSourceId']} rules={[{ required: true, message: '请选择 API 资源' }]}>
        <CanvasHttpApiResourceSelect dataSourceId={dataSourceId} />
      </Form.Item>
      {resourceId && resourceQuery.isError && <MetadataErrorAlert error={resourceQuery.error} fallback="读取 API 资源失败" onRetry={() => void resourceQuery.refetch()} />}
      <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true, message: '请输入输出表名' }, { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' }]}>
        <Input placeholder="例如 api_orders" />
      </Form.Item>
      <Typography.Text strong>运行时参数</Typography.Text>
      <Alert
        showIcon
        type="warning"
        title="运行时参数会随 Canvas 定义持久化"
        description="仅用于日期、业务筛选条件和初始游标等非敏感参数；Token、密码、API Key 和 Secret 必须配置在 HTTP API 数据源中。"
      />
      <Form.List name="runtimeParameters">
        {(fields, { add, remove }) => <Space orientation="vertical" size={8} className="canvas-condition-list">
          {fields.map((field, index) => <Card key={field.key} size="small" title={`参数 ${index + 1}`} extra={<Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除运行时参数 ${index + 1}`} onClick={() => remove(field.name)} />}>
            <Form.Item
              name={[field.name, 'name']}
              rules={[
                { required: true, message: '请输入参数名' },
                { pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/, message: '参数名格式无效' },
                {
                  validator: (_, value: string | undefined) => (
                    value && isSensitiveRuntimeParameterName(value)
                      ? Promise.reject(new Error('运行时参数不能用于密码、Token、API Key、Secret 或签名'))
                      : Promise.resolve()
                  ),
                },
              ]}
            >
              <Input placeholder="参数名，不含 runtime. 前缀" />
            </Form.Item>
            <Form.Item name={[field.name, 'value']} rules={[{ required: true }]}><Input placeholder="非敏感运行时值" /></Form.Item>
          </Card>)}
          <Button icon={<PlusOutlined />} onClick={() => add({ name: '', value: '' })}>添加运行时参数</Button>
        </Space>}
      </Form.List>
      {resourceQuery.data && <Card size="small" title={`输出字段 · ${resourceQuery.data.name}`}><FieldPreview columns={columns} loading={resourceQuery.isFetching} /></Card>}
    </Form>
  </Space>;
};

interface KafkaInputFormValues {
  dataSourceId: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  outputTableName: string;
  startingOffsets: KafkaInputConfiguration['startingOffsets'];
  triggerIntervalSeconds: number;
  valueFormat: KafkaInputValueFormat;
  metadataFields: KafkaInputMetadataField[];
}

const kafkaInputMetadataFieldOrder: KafkaInputMetadataField[] = [
  'KEY', 'TOPIC', 'PARTITION', 'OFFSET', 'TIMESTAMP',
];

const kafkaInputMetadataOptions = [
  { value: 'KEY', label: 'Key' },
  { value: 'TOPIC', label: 'Topic' },
  { value: 'PARTITION', label: 'Partition' },
  { value: 'OFFSET', label: 'Offset' },
  { value: 'TIMESTAMP', label: 'Timestamp' },
] satisfies Array<{ value: KafkaInputMetadataField; label: string }>;

export const KafkaInputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'KAFKA_INPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<KafkaInputFormValues>();
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const valueFormat = Form.useWatch('valueFormat', form) ?? 'JSON';
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceValid = dataSourceQuery.data
    ? kafkaDataSourceAvailable(dataSourceQuery.data, 'SOURCE')
    : dataSourceQuery.isError ? false : undefined;
  const toConfiguration = (values: KafkaInputFormValues): KafkaInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '',
    topic: values.topic?.trim() ?? '',
    valueSchema: values.valueFormat === 'JSON'
      ? values.valueSchema ?? { columns: [] }
      : { columns: [] },
    outputTableName: values.outputTableName?.trim() ?? '',
    startingOffsets: values.startingOffsets ?? null,
    triggerIntervalSeconds: values.triggerIntervalSeconds ?? 10,
    valueFormat: values.valueFormat ?? 'JSON',
    metadataFields: kafkaInputMetadataFieldOrder.filter(
      (field) => (values.metadataFields ?? []).includes(field),
    ),
  });
  const submit = (values: KafkaInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && dataSourceValid !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取 Kafka 数据源，请稍候'
              : 'Kafka 数据源不存在、已停用或不具有 SOURCE 用途'],
          }]);

        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<KafkaInputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{
          ...node.configuration,
          valueFormat: node.configuration.valueFormat ?? 'JSON',
          metadataFields: node.configuration.metadataFields ?? [],
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="dataSourceId" label="Kafka 数据源" rules={[{ required: true }]}>
          <CanvasKafkaDataSourceSelect purpose="SOURCE" placeholder="选择 Kafka 来源" />
        </Form.Item>
        <Form.Item name="topic" label="输入 Topic" rules={[{ required: true, whitespace: true }]}>
          <CanvasKafkaTopicSelect dataSourceId={dataSourceId} placeholder="搜索并选择 Topic" />
        </Form.Item>
        <Form.Item name="valueFormat" label="消息格式" rules={[{ required: true }]}>
          <Select options={[
            { value: 'JSON', label: 'JSON · 按 Value Schema 解析' },
            { value: 'TEXT', label: '文本 · UTF-8' },
            { value: 'BINARY', label: '二进制 · 保留原始字节' },
          ]} />
        </Form.Item>
        {valueFormat === 'JSON' ? (
          <Form.Item
            name="valueSchema"
            label="Value Schema"
            rules={[{
              validator: (_, value: KafkaValueSchema | undefined) => (
                value && value.columns.length > 0
                  ? Promise.resolve()
                  : Promise.reject(new Error('请至少定义一个 Value Schema 字段'))
              ),
            }]}
          >
            <KafkaValueSchemaEditor />
          </Form.Item>
        ) : (
          <Form.Item label="输出消息字段">
            <Space size={6}>
              <Typography.Text code>value</Typography.Text>
              <Tag>{valueFormat === 'TEXT' ? 'STRING' : 'BINARY'}</Tag>
              <Typography.Text type="secondary">可空</Typography.Text>
            </Space>
          </Form.Item>
        )}
        <Form.Item
          name="metadataFields"
          label={<CanvasInspectorFieldLabel
            label="Kafka 元数据"
            tooltip="按固定字段名追加到消息字段之后；Timestamp 不会自动成为事件时间。"
          />}
        >
          <Checkbox.Group options={kafkaInputMetadataOptions} />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出逻辑表名"
          rules={[
            { required: true, whitespace: true },
            { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' },
          ]}
        >
          <Input placeholder="例如 order_events" />
        </Form.Item>
        <Form.Item name="startingOffsets" label="首次启动位置" rules={[{ required: true }]}>
          <Select options={[
            { value: 'LATEST', label: 'LATEST · 从最新消息开始' },
            { value: 'EARLIEST', label: 'EARLIEST · 从最早消息开始' },
          ]} />
        </Form.Item>
        <Form.Item name="triggerIntervalSeconds" label="微批间隔（秒）" rules={[{ required: true }]}>
          <InputNumber min={1} max={300} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Alert
          type="info"
          showIcon
          title="首次启动位置仅对新 Checkpoint 生效"
          description="停止后恢复会继续使用当前部署的 Checkpoint，不会重新应用 EARLIEST/LATEST。"
        />
      </Form>
    </Space>
  );
};

interface JoinFormValues {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: JoinConfiguration['joinType'];
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
}

export const JoinInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'JOIN' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<JoinFormValues>();
  const leftName = Form.useWatch('leftTableName', form) ?? '';
  const rightName = Form.useWatch('rightTableName', form) ?? '';
  const outputColumns = Form.useWatch('outputColumns', form) ?? [];
  const conditions = Form.useWatch('conditions', form) ?? [];
  const tables = validation?.inputTables ?? [];
  const left = tables.find((table) => table.name === leftName);
  const right = tables.find((table) => table.name === rightName);
  const tableOptions = tables.map((table) => ({ value: table.name, label: table.name }));

  const toConfiguration = (values: JoinFormValues): JoinConfiguration => ({
      leftTableName: values.leftTableName ?? '',
      rightTableName: values.rightTableName ?? '',
      outputTableName: values.outputTableName?.trim() ?? '',
      joinType: values.joinType ?? null,
      conditions: (values.conditions ?? []).map((condition) => ({
        leftColumnName: condition.leftColumnName ?? '',
        operator: 'EQUALS',
        rightColumnName: condition.rightColumnName ?? '',
      })),
      outputColumns: (values.outputColumns ?? []).map((column) => ({
        sourceSide: column.sourceSide === 'RIGHT' ? 'RIGHT' : 'LEFT',
        sourceColumnName: column.sourceColumnName ?? '',
        outputColumnName: column.outputColumnName?.trim() ?? '',
        included: column.included !== false,
      })),
  });

  useEffect(() => {
    if (!left || !right || outputColumns.length > 0) return;
    form.setFieldValue('outputColumns', suggestJoinOutputColumns(left, right));
  }, [form, left, outputColumns.length, right]);

  const submit = (values: JoinFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<JoinFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <Form.Item name="leftTableName" label="左表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            options={tableOptions}
            placeholder={validation ? '选择左表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item name="rightTableName" label="右表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            options={tableOptions.filter((option) => option.value !== leftName)}
            placeholder={validation ? '选择右表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item name="joinType" label="Join 类型" rules={[{ required: true }]}>
          <Select options={['INNER', 'LEFT', 'RIGHT', 'FULL'].map((value) => ({ value, label: value }))} />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 order_customer" />
        </Form.Item>
        <Typography.Text strong>Join 条件</Typography.Text>
        <Form.List name="conditions">
          {(fields, { add, remove }) => (
            <Space orientation="vertical" size={8} className="canvas-condition-list">
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  size="small"
                  title={`条件 ${index + 1}`}
                  extra={<Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除条件 ${index + 1}`} onClick={() => remove(field.name)} />}
                >
                  <Form.Item name={[field.name, 'leftColumnName']} rules={[{ required: true }]}>
                    <Select disabled={!validation} placeholder="左表字段" options={left?.columns.map((column) => ({ value: column.name, label: `${column.name} · ${platformTypeLabel(column)}` })) ?? []} />
                  </Form.Item>
                  <div className="canvas-join-operator">=</div>
                  <Form.Item name={[field.name, 'rightColumnName']} rules={[{ required: true }]}>
                    <Select disabled={!validation} placeholder="右表字段" options={right?.columns.map((column) => ({ value: column.name, label: `${column.name} · ${platformTypeLabel(column)}` })) ?? []} />
                  </Form.Item>
                </Card>
              ))}
              <Button icon={<PlusOutlined />} onClick={() => add({ leftColumnName: '', operator: 'EQUALS', rightColumnName: '' })}>
                添加 Join 条件
              </Button>
            </Space>
          )}
        </Form.List>
        <JoinOutputColumnsEditor
          left={left}
          right={right}
          conditions={conditions}
          outputColumns={outputColumns}
          leftLabel="左"
          rightLabel="右"
          onProgrammaticChange={(columns) => {
            form.setFieldValue('outputColumns', columns);
            onDirtyChange(true);
          }}
        />
      </Form>
    </Space>
  );
};

interface StreamJoinFormValues {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: StreamJoinConfiguration['joinType'];
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
}

export const StreamJoinInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'STREAM_JOIN' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<StreamJoinFormValues>();
  const leftName = Form.useWatch('leftTableName', form) ?? '';
  const rightName = Form.useWatch('rightTableName', form) ?? '';
  const outputColumns = Form.useWatch('outputColumns', form) ?? [];
  const conditions = Form.useWatch('conditions', form) ?? [];
  const tables = validation?.inputTables ?? [];
  const left = tables.find((table) => table.name === leftName);
  const right = tables.find((table) => table.name === rightName);
  const unbounded = tables.filter((table) => table.datasetKind === 'UNBOUNDED');
  const bounded = tables.filter((table) => table.datasetKind === 'BOUNDED');
  const toConfiguration = (values: StreamJoinFormValues): StreamJoinConfiguration => ({
    leftTableName: values.leftTableName ?? '',
    rightTableName: values.rightTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    joinType: values.joinType ?? null,
    conditions: (values.conditions ?? []).map((condition) => ({
      leftColumnName: condition.leftColumnName ?? '',
      operator: 'EQUALS',
      rightColumnName: condition.rightColumnName ?? '',
    })),
    outputColumns: (values.outputColumns ?? []).map((column) => ({
      sourceSide: column.sourceSide === 'RIGHT' ? 'RIGHT' : 'LEFT',
      sourceColumnName: column.sourceColumnName ?? '',
      outputColumnName: column.outputColumnName?.trim() ?? '',
      included: column.included !== false,
    })),
  });

  useEffect(() => {
    if (!left || !right || outputColumns.length > 0) return;
    form.setFieldValue('outputColumns', suggestJoinOutputColumns(left, right));
  }, [form, left, outputColumns.length, right]);

  const submit = (values: StreamJoinFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Alert
        type="info"
        showIcon
        title="第一阶段仅支持流表关联静态维表"
        description="左表必须是无界 Kafka 流，右表必须是启动时加载的有界 JDBC 维表。"
      />
      <Form<StreamJoinFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="leftTableName" label="左侧流表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            options={unbounded.map((table) => ({ value: table.name, label: table.name }))}
            placeholder={validation ? '选择无界流表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item name="rightTableName" label="右侧静态维表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            options={bounded.map((table) => ({ value: table.name, label: table.name }))}
            placeholder={validation ? '选择有界静态表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item name="joinType" label="Join 类型" rules={[{ required: true }]}>
          <Select options={[
            { value: 'INNER', label: 'INNER' },
            { value: 'LEFT', label: 'LEFT' },
          ]} />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出流表名"
          rules={[{ required: true, whitespace: true }]}
        >
          <Input placeholder="例如 order_customer_stream" />
        </Form.Item>
        <Typography.Text strong>等值条件</Typography.Text>
        <Form.List name="conditions">
          {(fields, { add, remove }) => (
            <Space orientation="vertical" size={8} className="canvas-condition-list">
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  size="small"
                  title={`条件 ${index + 1}`}
                  extra={(
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除流 Join 条件 ${index + 1}`}
                      onClick={() => remove(field.name)}
                    />
                  )}
                >
                  <Form.Item name={[field.name, 'leftColumnName']} rules={[{ required: true }]}>
                    <Select
                      placeholder="流表字段"
                      options={left?.columns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${platformTypeLabel(column)}`,
                      })) ?? []}
                    />
                  </Form.Item>
                  <div className="canvas-join-operator">=</div>
                  <Form.Item name={[field.name, 'rightColumnName']} rules={[{ required: true }]}>
                    <Select
                      placeholder="维表字段"
                      options={right?.columns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${platformTypeLabel(column)}`,
                      })) ?? []}
                    />
                  </Form.Item>
                </Card>
              ))}
              <Button
                icon={<PlusOutlined />}
                onClick={() => add({
                  leftColumnName: '',
                  operator: 'EQUALS',
                  rightColumnName: '',
                })}
              >
                添加等值条件
              </Button>
            </Space>
          )}
        </Form.List>
        <JoinOutputColumnsEditor
          left={left}
          right={right}
          conditions={conditions}
          outputColumns={outputColumns}
          leftLabel="流"
          rightLabel="维"
          onProgrammaticChange={(columns) => {
            form.setFieldValue('outputColumns', columns);
            onDirtyChange(true);
          }}
        />
      </Form>
    </Space>
  );
};

interface RenameFormValues {
  sourceTableName: string;
  outputTableName: string;
}

interface RenameFieldRow {
  key: string;
  sourceColumnName: string;
  targetColumnName: string;
  column: CanvasColumnSchema | null;
  changed: boolean;
  issue: string | null;
}

export const RenameInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'RENAME' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<RenameFormValues>();
  const [search, setSearch] = useState('');
  const [changedOnly, setChangedOnly] = useState(false);
  const [mappingTargets, setMappingTargets] = useState<Record<string, string>>(() => Object.fromEntries(
    node.configuration.columnMappings.map((mapping) => [
      mapping.sourceColumnName,
      mapping.targetColumnName,
    ]),
  ));
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const tableOptions = (validation?.inputTables ?? [])
    .map((table) => ({ value: table.name, label: table.name }));

  const rows = useMemo<RenameFieldRow[]>(() => {
    const sourceColumns = source?.columns ?? [];
    const sourceColumnMap = new Map(sourceColumns.map((column) => [column.name, column]));
    const sourceColumnNames = [
      ...sourceColumns.map((column) => column.name),
      ...Object.keys(mappingTargets).filter((name) => !sourceColumnMap.has(name)),
    ];
    const targetNames = sourceColumnNames.map((name) => mappingTargets[name] ?? name);
    const targetCounts = targetNames.reduce((counts, name) => {
      if (name) counts.set(name, (counts.get(name) ?? 0) + 1);
      return counts;
    }, new Map<string, number>());
    return sourceColumnNames.map((sourceColumnName) => {
      const targetColumnName = mappingTargets[sourceColumnName] ?? sourceColumnName;
      const column = sourceColumnMap.get(sourceColumnName) ?? null;
      const issue = !column
        ? '来源字段已失效，可恢复原名以移除这条旧映射'
        : !targetColumnName.trim()
          ? '新字段名不能为空'
          : (targetCounts.get(targetColumnName) ?? 0) > 1
            ? `新字段名重复：${targetColumnName}`
            : null;
      return {
        key: sourceColumnName,
        sourceColumnName,
        targetColumnName,
        column,
        changed: targetColumnName !== sourceColumnName,
        issue,
      };
    });
  }, [mappingTargets, source?.columns]);

  const changedCount = rows.filter((row) => row.changed).length;
  const invalidCount = rows.filter((row) => row.issue !== null).length;
  const visibleRows = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase();
    return rows.filter((row) => {
      if (changedOnly && !row.changed) return false;
      if (!keyword) return true;
      return [row.sourceColumnName, row.targetColumnName, row.column?.comment]
        .some((value) => value?.toLocaleLowerCase().includes(keyword));
    });
  }, [changedOnly, rows, search]);

  const buildMappings = (): JdbcColumnMapping[] => rows
    .filter((row) => row.changed)
    .map((row) => ({
      sourceColumnName: row.sourceColumnName,
      targetColumnName: row.targetColumnName,
    }));

  const toConfiguration = (values: RenameFormValues): RenameConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    columnMappings: buildMappings(),
  });

  const submit = (values: RenameFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const updateTargetName = (sourceColumnName: string, targetColumnName: string) => {
    setMappingTargets((current) => {
      const next = { ...current };
      if (targetColumnName === sourceColumnName) delete next[sourceColumnName];
      else next[sourceColumnName] = targetColumnName;
      return next;
    });
    onDirtyChange(true);
  };

  const columns: TableColumnsType<RenameFieldRow> = [
    {
      title: '描述',
      key: 'description',
      width: '50%',
      ellipsis: true,
      render: (_, row) => (
        row.column?.comment ? (
          <Typography.Text
            type="secondary"
            className="canvas-rename-source-comment"
            ellipsis={{ tooltip: row.column.comment }}
          >
            {row.column.comment}
          </Typography.Text>
        ) : <Typography.Text type="secondary">—</Typography.Text>
      ),
    },
    {
      title: '源字段名',
      key: 'sourceColumnName',
      width: '25%',
      ellipsis: true,
      render: (_, row) => (
        <Tooltip
          title={row.column
            ? `${platformTypeLabel(row.column)} · ${row.column.nullable ? '可空' : '非空'}`
            : '来源字段已失效'}
        >
          <Typography.Text className="canvas-rename-source-code" ellipsis>
            {row.sourceColumnName}
          </Typography.Text>
        </Tooltip>
      ),
    },
    {
      title: '新字段名',
      key: 'targetColumnName',
      width: '25%',
      render: (_, row) => (
        <Tooltip title={row.issue} open={row.issue ? undefined : false}>
          <Input
            size="small"
            status={row.issue ? 'error' : undefined}
            value={row.targetColumnName}
            aria-label={`重命名 ${row.sourceColumnName}`}
            onChange={(event) => updateTargetName(row.sourceColumnName, event.target.value)}
            suffix={row.changed ? (
              <Tooltip title="恢复原名">
                <Button
                  type="text"
                  size="small"
                  className="canvas-rename-reset-button"
                  icon={<UndoOutlined />}
                  aria-label={`恢复 ${row.sourceColumnName} 原名`}
                  onClick={() => updateTargetName(row.sourceColumnName, row.sourceColumnName)}
                />
              </Tooltip>
            ) : null}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<RenameFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择需要重命名的上游表' : '等待 Task Engine 计算上游表'}
            options={tableOptions}
            onChange={(value) => {
              if (!form.getFieldValue('outputTableName')) {
                form.setFieldValue('outputTableName', value);
              }
            }}
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出逻辑表名"
          extra="只重命名字段时保持与来源表相同"
          rules={[{ required: true, whitespace: true, message: '请输入输出逻辑表名' }]}
        >
          <Input placeholder="例如 source_orders" />
        </Form.Item>
        <div className="canvas-rename-field-heading">
          <Typography.Text strong>字段重命名</Typography.Text>
          <Typography.Text type="secondary">
            直接修改右侧名称；未修改字段不会写入映射。
          </Typography.Text>
        </div>
        <div className="canvas-rename-field-toolbar">
          <Input
            size="small"
            allowClear
            prefix={<SearchOutlined />}
            value={search}
            placeholder="搜索字段或描述"
            onChange={(event) => setSearch(event.target.value)}
          />
          <Checkbox
            checked={changedOnly}
            disabled={changedCount === 0}
            onChange={(event) => setChangedOnly(event.target.checked)}
          >
            仅显示已修改
          </Checkbox>
          <Typography.Text type={invalidCount > 0 ? 'danger' : 'secondary'}>
            已修改 {changedCount} / 总字段 {rows.length}{invalidCount > 0 ? ` · ${invalidCount} 项有误` : ''}
          </Typography.Text>
        </div>
        <Table<RenameFieldRow>
          className="canvas-rename-field-table"
          size="small"
          rowKey="key"
          columns={columns}
          dataSource={visibleRows}
          pagination={false}
          scroll={{ y: 480 }}
          locale={{ emptyText: source ? '没有符合条件的字段' : '等待来源表 Schema' }}
          rowClassName={(row) => row.issue ? 'is-invalid' : row.changed ? 'is-changed' : ''}
        />
      </Form>
    </Space>
  );
};

interface JdbcOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName?: string;
  writeMode: JdbcOutputConfiguration['writeMode'];
  upsertKeySelection?: string;
  columnMappings: JdbcColumnMapping[];
}

const serializeUpsertKeyColumns = (columns: string[]): string | undefined => (
  columns.length > 0 ? JSON.stringify(columns) : undefined
);

const parseUpsertKeyColumns = (value: string | undefined): string[] => {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) && parsed.every((column) => typeof column === 'string')
      ? parsed
      : [];
  } catch {
    return [];
  }
};

export const JdbcOutputInspector = ({
  node,
  executionMode = 'BATCH',
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
  splitLayout = false,
  hideDataSource = false,
  hideNodeValidation = false,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'JDBC_OUTPUT' }>;
  executionMode: CanvasExecutionMode;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
  splitLayout?: boolean;
  hideDataSource?: boolean;
  hideNodeValidation?: boolean;
}) => {
  const [form] = Form.useForm<JdbcOutputFormValues>();
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const selectedDataSourceId = Form.useWatch(
    'dataSourceId',
    { form, preserve: true },
  ) ?? '';
  const selectedTableName = Form.useWatch('targetTableName', form) ?? '';
  const writeMode = Form.useWatch('writeMode', form) ?? null;
  const upsertKeySelection = Form.useWatch('upsertKeySelection', form);
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const selectedDataSourceQuery = useDataSource(selectedDataSourceId || undefined, Boolean(selectedDataSourceId));
  const selectedTableQuery = useTableMetadata(
    selectedDataSourceId || undefined,
    selectedTableName ? { catalog: null, schema: null, table: selectedTableName } : undefined,
    Boolean(selectedDataSourceId && selectedTableName),
  );
  const selectedDataSourceAvailable = selectedDataSourceQuery.data
    ? dataSourceAvailable(selectedDataSourceQuery.data, 'DISTRIBUTION')
    : selectedDataSourceQuery.isError ? false : undefined;
  const selectedTableAvailable = selectedTableQuery.data
    ? true
    : selectedTableQuery.isError ? false : undefined;
  const selectedTargetColumns = canvasColumns(selectedTableQuery.data);
  const uniqueKeys = selectedTableQuery.data?.uniqueKeys ?? [];
  const primaryKeyColumns = uniqueKeys.find((key) => key.type === 'PRIMARY_KEY')?.columns ?? [];
  const uniqueKeyOptions = uniqueKeys.map((key) => {
    const keyColumns = new Set(key.columns);
    const forbiddenColumns = selectedTableQuery.data?.columns.filter((column) => (
      keyColumns.has(column.name)
      && (column.autoIncrement || column.generated || column.platformTypeDefinition?.type === 'GEOMETRY')
    )).map((column) => column.name) ?? [];
    const prefix = key.type === 'PRIMARY_KEY'
      ? '主键'
      : key.name ? `唯一索引 ${key.name}` : '唯一索引';
    return {
      value: JSON.stringify(key.columns),
      label: `${prefix}：${key.columns.join(', ')}${forbiddenColumns.length > 0 ? '（包含不可用字段）' : ''}`,
      disabled: forbiddenColumns.length > 0,
    };
  });
  const selectedKeyMatches = upsertKeySelection
    ? uniqueKeyOptions.some((option) => option.value === upsertKeySelection)
    : false;
  const selectedKeyInvalid = writeMode === 'UPSERT'
    && Boolean(upsertKeySelection)
    && selectedTableQuery.data !== undefined
    && !selectedKeyMatches;
  const upsertKeySelectOptions = selectedKeyInvalid && upsertKeySelection
    ? [{
      value: upsertKeySelection,
      label: `已保存但目标约束已失效：${parseUpsertKeyColumns(upsertKeySelection).join(', ') || '未知字段'}`,
      disabled: true,
    }, ...uniqueKeyOptions]
    : uniqueKeyOptions;
  const mysqlHasMultipleUniqueKeys = selectedDataSourceQuery.data?.type === 'MYSQL'
    && uniqueKeys.length > 1;
  const targetDatabaseType = selectedDataSourceQuery.data?.type;
  const currentWriteModeUnavailableReason = writeMode
    ? jdbcWriteModeUnavailableReason(targetDatabaseType, writeMode, executionMode)
    : null;
  const writeModeOptions = (['APPEND', 'OVERWRITE', 'UPSERT'] as const).map((mode) => {
    const unavailableReason = jdbcWriteModeUnavailableReason(
      targetDatabaseType,
      mode,
      executionMode,
    );
    const action = mode === 'APPEND'
      ? '追加'
      : mode === 'OVERWRITE' ? '清空后写入' : '按唯一键插入或更新';
    return {
      value: mode,
      label: `${mode} · ${unavailableReason ?? action}`,
      disabled: Boolean(unavailableReason),
    };
  });

  const toConfiguration = (values: JdbcOutputFormValues): JdbcOutputConfiguration => ({
      sourceTableName: values.sourceTableName ?? '',
      dataSourceId: values.dataSourceId ?? '',
      targetTableName: values.targetTableName ?? '',
      writeMode: values.writeMode ?? null,
      upsertKeyColumns: values.writeMode === 'UPSERT'
        ? parseUpsertKeyColumns(values.upsertKeySelection)
        : [],
      columnMappings: orderOutputFieldMappings(
        selectedTargetColumns,
        (values.columnMappings ?? []).map((mapping) => ({
          sourceColumnName: mapping.sourceColumnName ?? '',
          targetColumnName: mapping.targetColumnName ?? '',
        })),
      ),
      writes: [{
        writeId: node.configuration.writes?.[0]?.writeId ?? crypto.randomUUID(),
        sourceTableName: values.sourceTableName ?? '', targetTableName: values.targetTableName ?? '',
        writeMode: values.writeMode ?? null,
        upsertKeyColumns: values.writeMode === 'UPSERT' ? parseUpsertKeyColumns(values.upsertKeySelection) : [],
        columnMappings: orderOutputFieldMappings(selectedTargetColumns, (values.columnMappings ?? []).map((mapping) => ({ sourceColumnName: mapping.sourceColumnName ?? '', targetColumnName: mapping.targetColumnName ?? '' }))),
      }],
  });

  const submit = (values: JdbcOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && selectedDataSourceAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [selectedDataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '数据源不存在、已停用或不具有数据分发用途'],
          }]);

        }
        if (values.targetTableName && selectedTableAvailable !== true) {
          form.setFields([{
            name: 'targetTableName',
            errors: [selectedTableQuery.isFetching
              ? '正在读取目标表元数据，请稍候'
              : '该目标表不存在或不属于当前数据源'],
          }]);

        }
        if (values.writeMode) {
          const unavailableReason = jdbcWriteModeUnavailableReason(
            selectedDataSourceQuery.data?.type,
            values.writeMode,
            executionMode,
          );
          if (unavailableReason) {
            form.setFields([{ name: 'writeMode', errors: [unavailableReason] }]);
          }
        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  return (
    <Space orientation="vertical" size={12} className={`canvas-inspector-content${splitLayout ? ' canvas-output-write-editor' : ''}`}>
      {!hideNodeValidation && <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
      <Form<JdbcOutputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        className={splitLayout ? 'canvas-output-write-editor-form' : undefined}
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          dataSourceId: node.configuration.dataSourceId,
          targetTableName: node.configuration.targetTableName || undefined,
          writeMode: node.configuration.writeMode,
          upsertKeySelection: serializeUpsertKeyColumns(node.configuration.upsertKeyColumns),
          columnMappings: node.configuration.columnMappings,
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <div className={splitLayout ? 'canvas-output-write-editor-grid' : undefined}>
        <div className={splitLayout ? 'canvas-output-write-editor-settings' : undefined}>
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={(validation?.inputTables ?? []).map((table) => ({ value: table.name, label: table.name }))}
          />
        </Form.Item>
        {!hideDataSource && <Form.Item
          name="dataSourceId"
          label="目标数据源"
          rules={[
            { required: true, message: '请选择目标数据源' },
            {
              validator: async () => {
                if (!selectedDataSourceId) return;
                if (selectedDataSourceQuery.isFetching && !selectedDataSourceQuery.data) {
                  throw new Error('正在读取数据源信息，请稍候');
                }
                if (selectedDataSourceAvailable === false) {
                  throw new Error('数据源不存在、已停用或不具有数据分发用途');
                }
              },
            },
          ]}
        >
          <CanvasJdbcDataSourceSelect
            purpose="DISTRIBUTION"
            placeholder="选择 JDBC 数据分发数据源"
          />
        </Form.Item>}
        {!hideDataSource && selectedDataSourceId && selectedDataSourceQuery.isError && (
          <MetadataErrorAlert
            error={selectedDataSourceQuery.error}
            fallback="读取目标数据源信息失败"
            onRetry={() => void selectedDataSourceQuery.refetch()}
          />
        )}
        <Form.Item
          name="targetTableName"
          label="目标物理表"
          dependencies={['dataSourceId']}
          rules={[
            { required: true, message: '请选择目标物理表' },
            {
              validator: async () => {
                if (!selectedTableName) return;
                if (selectedTableQuery.isFetching && !selectedTableQuery.data) {
                  throw new Error('正在读取目标表元数据，请稍候');
                }
                if (selectedTableAvailable === false) {
                  throw new Error('该目标表不存在或不属于当前数据源');
                }
              },
            },
          ]}
        >
          <CanvasJdbcTableSelect
            dataSourceId={selectedDataSourceId}
            placeholder="输入表名搜索真实目标表"
            selectedTableAvailable={selectedTableAvailable}
          />
        </Form.Item>
        {selectedTableName && selectedTableQuery.isError && (
          <MetadataErrorAlert
            error={selectedTableQuery.error}
            fallback="读取目标表元数据失败"
            onRetry={() => void selectedTableQuery.refetch()}
          />
        )}
        <Form.Item
          name="writeMode"
          label={(
            <CanvasInspectorFieldLabel
              label="写入模式"
              tooltip={executionMode === 'STREAMING'
                ? '实时任务通过 foreachBatch 执行 APPEND 或 UPSERT，整体按至少一次交付。'
                : 'OVERWRITE 会先执行 TRUNCATE TABLE，再 APPEND 写入。两步不是同一原子事务；后续写入失败时，目标表可能为空或仅部分写入。'}
            />
          )}
          rules={[{ required: true }]}
          validateStatus={currentWriteModeUnavailableReason ? 'error' : undefined}
          help={currentWriteModeUnavailableReason ?? undefined}
        >
          <Select options={writeModeOptions} />
        </Form.Item>
        {writeMode === 'UPSERT' && (
          <>
            <Form.Item
              name="upsertKeySelection"
              label={(
                <CanvasInspectorFieldLabel
                  label="UPSERT 唯一键"
                  tooltip="必须选择一整组主键或唯一索引。定义只保存字段名及数据库返回顺序，不保存约束名称。"
                />
              )}
              dependencies={['dataSourceId', 'targetTableName']}
              rules={[
                { required: true, message: '请选择目标表的一整组主键或唯一索引字段' },
                {
                  validator: async (_, value: string | undefined) => {
                    if (!value) return;
                    if (selectedTableQuery.isFetching && !selectedTableQuery.data) {
                      throw new Error('正在读取目标表唯一键，请稍候');
                    }
                    if (selectedTableQuery.data
                      && !uniqueKeyOptions.some((option) => option.value === value && !option.disabled)) {
                      throw new Error('已保存的 UPSERT Key 不再是目标表可用的完整唯一约束');
                    }
                  },
                },
              ]}
            >
              <Select
                placeholder={selectedTableQuery.isFetching ? '正在读取唯一键…' : '选择主键或唯一索引'}
                loading={selectedTableQuery.isFetching}
                disabled={!selectedTableName || selectedTableQuery.isFetching}
                status={selectedKeyInvalid ? 'error' : undefined}
                options={upsertKeySelectOptions}
              />
            </Form.Item>
            {selectedTableQuery.data && uniqueKeys.length === 0 && (
              <Alert
                showIcon
                type="warning"
                title="目标表没有可用于 UPSERT 的唯一键"
                description="请先在数据库中创建主键或普通字段型唯一索引，再刷新目标表元数据。"
              />
            )}
            {mysqlHasMultipleUniqueKeys && (
              <Alert
                showIcon
                type="warning"
                title="MySQL 可能由任意唯一约束触发更新"
                description="MySQL 使用 ON DUPLICATE KEY UPDATE。即使选择其中一组 Key，其他主键或唯一索引冲突也可能触发更新。"
              />
            )}
          </>
        )}
        </div>
        <div className={splitLayout ? 'canvas-output-write-editor-mappings' : undefined}>
        <OutputFieldMappingFields
          sourceColumns={source?.columns ?? []}
          targetColumns={selectedTargetColumns}
          primaryKeyColumns={primaryKeyColumns}
          initialMappings={node.configuration.columnMappings ?? []}
          sourceReady={Boolean(validation && source)}
          targetReady={Boolean(selectedTableQuery.data)}
          targetLoading={selectedTableQuery.isFetching}
          keyColumns={writeMode === 'UPSERT' ? parseUpsertKeyColumns(upsertKeySelection) : []}
          keyLabel="UPSERT Key"
          scrollHeight={splitLayout ? 520 : undefined}
          onProgrammaticChange={() => queueMicrotask(() => {
            const values = form.getFieldsValue(true);
            onDirtyChange(
              configurationFingerprint(toConfiguration(values))
                !== configurationFingerprint(node.configuration),
            );
          })}
        />
        </div>
        </div>
      </Form>
    </Space>
  );
};

interface KafkaOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  topic: string;
  valueFormat: KafkaOutputValueFormat | null;
  valueColumnNames: string[];
  keyColumnName?: string;
  valueSchema: KafkaValueSchema | null;
  columnMappings: JdbcColumnMapping[];
}

const normalizeKafkaValueColumnNames = (
  valueFormat: KafkaOutputValueFormat,
  columnNames: readonly string[],
) => {
  const uniqueColumnNames = [...new Set(columnNames)];
  return valueFormat === 'JSON' ? uniqueColumnNames : uniqueColumnNames.slice(0, 1);
};

export const KafkaOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
  splitLayout = false,
  hideDataSource = false,
  hideNodeValidation = false,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'KAFKA_OUTPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
  splitLayout?: boolean;
  hideDataSource?: boolean;
  hideNodeValidation?: boolean;
}) => {
  const [form] = Form.useForm<KafkaOutputFormValues>();
  const [valueFieldSearch, setValueFieldSearch] = useState('');
  const initialWrite = node.configuration.writes?.[0];
  const legacyMode = initialWrite?.valueFormat == null;
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const dataSourceId = Form.useWatch(
    'dataSourceId',
    { form, preserve: true },
  ) ?? '';
  const valueFormat = Form.useWatch('valueFormat', form) ?? null;
  const valueColumnNames = Form.useWatch('valueColumnNames', { form, preserve: true }) ?? [];
  const valueSchema = Form.useWatch('valueSchema', form) ?? { columns: [] };
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceValid = dataSourceQuery.data
    ? kafkaDataSourceAvailable(dataSourceQuery.data, 'DISTRIBUTION')
    : dataSourceQuery.isError ? false : undefined;
  const targetFields = valueSchema.columns;
  const targetColumns: CanvasColumnSchema[] = targetFields.map((field) => ({
    name: field.name,
    fieldType: field.fieldType,
    length: field.length,
    precision: field.precision,
    scale: field.scale,
    nullable: field.nullable,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: field.comment,
    geometry: null,
  }));
  const orderedValueColumnNames = (names: string[]) => {
    const uniqueNames = normalizeKafkaValueColumnNames('JSON', names);
    const selected = new Set(uniqueNames);
    const known = source?.columns.filter((column) => selected.has(column.name))
      .map((column) => column.name) ?? [];
    const knownSet = new Set(known);
    return [...known, ...uniqueNames.filter((name) => !knownSet.has(name))];
  };
  const normalizedValueColumnNames = (
    format: KafkaOutputValueFormat,
    names: string[],
  ) => normalizeKafkaValueColumnNames(format, orderedValueColumnNames(names));
  const toConfiguration = (values: KafkaOutputFormValues): KafkaOutputConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    dataSourceId: values.dataSourceId ?? '',
    topic: values.topic?.trim() ?? '',
    valueSchema: legacyMode ? values.valueSchema ?? { columns: [] } : { columns: [] },
    keyColumnName: values.keyColumnName ?? '',
    columnMappings: legacyMode ? orderOutputFieldMappings(
      targetColumns,
      (values.columnMappings ?? []).map((mapping) => ({
        sourceColumnName: mapping.sourceColumnName ?? '',
        targetColumnName: mapping.targetColumnName ?? '',
      })),
    ) : [],
    writes: [{
      writeId: node.configuration.writes?.[0]?.writeId ?? crypto.randomUUID(),
      sourceTableName: values.sourceTableName ?? '', topic: values.topic?.trim() ?? '',
      valueFormat: legacyMode ? null : values.valueFormat ?? 'JSON',
      valueColumnNames: legacyMode ? [] : normalizedValueColumnNames(
        values.valueFormat ?? 'JSON',
        values.valueColumnNames ?? [],
      ),
      keyColumnName: values.keyColumnName ?? '',
      valueSchema: legacyMode ? values.valueSchema ?? { columns: [] } : null,
      columnMappings: legacyMode
        ? orderOutputFieldMappings(targetColumns, (values.columnMappings ?? []).map((mapping) => ({ sourceColumnName: mapping.sourceColumnName ?? '', targetColumnName: mapping.targetColumnName ?? '' })))
        : [],
    }],
  });
  const submit = (values: KafkaOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && dataSourceValid !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取 Kafka 数据源，请稍候'
              : 'Kafka 数据源不存在、已停用或不具有 DISTRIBUTION 用途'],
          }]);

        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const selectableValueColumns = source?.columns.filter((column) => (
    valueFormat === 'JSON'
    || valueFormat === 'TEXT' && column.fieldType === 'STRING'
    || valueFormat === 'BINARY' && column.fieldType === 'BINARY'
  )) ?? [];
  const visibleValueColumns = selectableValueColumns.filter((column) => {
    const keyword = valueFieldSearch.trim().toLowerCase();
    return !keyword || column.name.toLowerCase().includes(keyword)
      || (column.comment ?? '').toLowerCase().includes(keyword);
  });
  const missingValueColumns = valueColumnNames.filter(
    (name) => !source?.columns.some((column) => column.name === name),
  );
  const keyOptions = (source?.columns ?? [])
    .filter((column) => legacyMode
      || column.fieldType === 'STRING' || column.fieldType === 'BINARY')
    .map((column) => ({
      value: column.name,
      label: `${column.name} · ${platformTypeLabel(column)}`,
    }));
  const currentKey = Form.useWatch('keyColumnName', form) ?? '';
  const retainedKeyOptions = currentKey && !keyOptions.some((option) => option.value === currentKey)
    ? [{ value: currentKey, label: `已失效 · ${currentKey}`, disabled: true }, ...keyOptions]
    : keyOptions;
  const resetValueSelection = (nextSourceName: string, nextFormat: KafkaOutputValueFormat) => {
    const nextSource = validation?.inputTables.find((table) => table.name === nextSourceName);
    const candidates = nextSource?.columns.filter((column) => (
      nextFormat === 'JSON'
      || nextFormat === 'TEXT' && column.fieldType === 'STRING'
      || nextFormat === 'BINARY' && column.fieldType === 'BINARY'
    )) ?? [];
    form.setFieldValue(
      'valueColumnNames',
      normalizeKafkaValueColumnNames(nextFormat, candidates.map((column) => column.name)),
    );
  };

  return (
    <Space orientation="vertical" size={12} className={`canvas-inspector-content${splitLayout ? ' canvas-output-write-editor' : ''}`}>
      {!hideNodeValidation && <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
      <Form<KafkaOutputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        className={splitLayout ? 'canvas-output-write-editor-form' : undefined}
        initialValues={{
          ...node.configuration,
          sourceTableName: initialWrite?.sourceTableName ?? node.configuration.sourceTableName,
          topic: initialWrite?.topic ?? node.configuration.topic,
          valueFormat: initialWrite?.valueFormat ?? null,
          valueColumnNames: initialWrite?.valueFormat == null
            ? []
            : normalizeKafkaValueColumnNames(
              initialWrite.valueFormat,
              initialWrite.valueColumnNames,
            ),
          keyColumnName: initialWrite?.keyColumnName ?? node.configuration.keyColumnName,
          valueSchema: initialWrite?.valueSchema ?? node.configuration.valueSchema,
          columnMappings: initialWrite?.columnMappings ?? node.configuration.columnMappings,
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(toConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <div className={splitLayout ? 'canvas-output-write-editor-grid' : undefined}>
        <div className={splitLayout ? 'canvas-output-write-editor-settings' : undefined}>
        <Form.Item name="sourceTableName" label="来源流表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择无界上游流表' : '等待 Task Engine 计算上游表'}
            options={(validation?.inputTables ?? [])
              .filter((table) => table.datasetKind === 'UNBOUNDED')
              .map((table) => ({ value: table.name, label: table.name }))}
            onChange={(value) => {
              if (!legacyMode) {
                form.setFieldValue('keyColumnName', '');
                resetValueSelection(value, valueFormat ?? 'JSON');
              }
            }}
          />
        </Form.Item>
        {!hideDataSource && <Form.Item name="dataSourceId" label="Kafka 数据源" rules={[{ required: true }]}>
          <CanvasKafkaDataSourceSelect purpose="DISTRIBUTION" placeholder="选择 Kafka 输出" />
        </Form.Item>}
        <Form.Item
          name="topic"
          label={(
            <CanvasInspectorFieldLabel
              label="输出 Topic"
              tooltip="Kafka Sink 按至少一次处理，故障恢复可能重复发送；多个输出之间没有跨 Sink 事务。"
            />
          )}
          rules={[{ required: true, whitespace: true }]}
        >
          <CanvasKafkaTopicSelect dataSourceId={dataSourceId} placeholder="搜索并选择 Topic" />
        </Form.Item>
        {legacyMode ? <>
          <Form.Item label="Value 模式"><Tag color="gold">旧版 JSON Schema + 字段映射</Tag></Form.Item>
          <Form.Item
            name="valueSchema"
            label="Value Schema"
            rules={[{
              validator: (_, value: KafkaValueSchema | undefined) => (
                value && value.columns.length > 0
                  ? Promise.resolve()
                  : Promise.reject(new Error('请至少定义一个 Value Schema 字段'))
              ),
            }]}
          >
            <KafkaValueSchemaEditor />
          </Form.Item>
        </> : <Form.Item name="valueFormat" label="Value 格式" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'JSON', label: 'JSON · 多字段组成对象' },
              { value: 'TEXT', label: 'TEXT · 原样发送 STRING' },
              { value: 'BINARY', label: 'BINARY · 原样发送字节' },
            ]}
            onChange={(format: KafkaOutputValueFormat) => resetValueSelection(sourceName, format)}
          />
        </Form.Item>}
        <Form.Item name="keyColumnName" label="Key 字段（可选）">
          <Select
            allowClear
            disabled={!source}
            placeholder="不设置时 Kafka Key 为空"
            options={retainedKeyOptions}
          />
        </Form.Item>
        </div>
        <div className={splitLayout ? 'canvas-output-write-editor-mappings' : undefined}>
        {legacyMode ? <OutputFieldMappingFields
          sourceColumns={source?.columns ?? []}
          targetColumns={targetColumns}
          initialMappings={node.configuration.columnMappings ?? []}
          sourceReady={Boolean(validation && source)}
          targetReady={targetColumns.length > 0}
          scrollHeight={splitLayout ? 520 : undefined}
          onProgrammaticChange={() => queueMicrotask(() => {
            const values = form.getFieldsValue(true);
            onDirtyChange(
              configurationFingerprint(toConfiguration(values))
                !== configurationFingerprint(node.configuration),
            );
          })}
        /> : <Space orientation="vertical" size={8} style={{ width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <Typography.Text strong>{valueFormat === 'JSON' ? '选择 JSON Value 字段' : `选择 ${valueFormat ?? ''} Value 字段`}</Typography.Text>
            <Typography.Text type="secondary">已选 {valueColumnNames.length} / {selectableValueColumns.length}</Typography.Text>
          </div>
          <Input
            size="small"
            allowClear
            prefix={<SearchOutlined />}
            value={valueFieldSearch}
            placeholder="搜索字段或描述"
            onChange={(event) => setValueFieldSearch(event.target.value)}
          />
          {missingValueColumns.length > 0 && <Typography.Text type="danger">
            已保存但上游失效：{missingValueColumns.join('、')}
          </Typography.Text>}
          {valueFormat === 'JSON' ? <Table<CanvasColumnSchema>
            size="small"
            rowKey="name"
            pagination={false}
            scroll={{ y: 500 }}
            dataSource={visibleValueColumns}
            rowSelection={{
              preserveSelectedRowKeys: true,
              selectedRowKeys: valueColumnNames,
              onChange: (keys) => form.setFieldValue(
                'valueColumnNames', orderedValueColumnNames(keys.map(String)),
              ),
            }}
            columns={[
              { title: '字段', dataIndex: 'name', ellipsis: true },
              { title: '类型', width: 132, render: (_, column) => platformTypeLabel(column) },
              { title: '描述', dataIndex: 'comment', ellipsis: true, render: (comment: string | null) => comment || '—' },
            ]}
            locale={{ emptyText: source ? '没有可选字段' : '等待来源流表 Schema' }}
          /> : <Select
            showSearch
            allowClear
            value={valueColumnNames[0]}
            placeholder={valueFormat === 'TEXT' ? '选择一个 STRING 字段' : '选择一个 BINARY 字段'}
            options={selectableValueColumns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${platformTypeLabel(column)}`,
            }))}
            onChange={(value) => form.setFieldValue(
              'valueColumnNames',
              normalizeKafkaValueColumnNames(valueFormat ?? 'TEXT', value ? [value] : []),
            )}
          />}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {valueFormat === 'JSON'
              ? '字段按上游 Schema 顺序组成 JSON 对象；字段改名或类型转换请在前置 Processor 完成。'
              : valueFormat === 'TEXT'
                ? 'STRING 内容按 UTF-8 原样发送，NULL 会产生 Kafka tombstone。'
                : 'BINARY 字节原样发送，NULL 会产生 Kafka tombstone。'}
          </Typography.Text>
        </Space>}
        </div>
        </div>
      </Form>
    </Space>
  );
};

const retainedOption = (
  options: { value: string; label: string; disabled?: boolean }[],
  value: string,
  unavailableLabel: string,
) => {
  if (!value || options.some((option) => option.value === value)) return options;
  return [{ value, label: unavailableLabel, disabled: true }, ...options];
};

const sortedModelFields = (detail: DataModelDetail | undefined) => (
  [...(detail?.fields ?? [])].sort((left, right) => left.sortOrder - right.sortOrder)
);

const modelFieldTypeLabel = (field: DataModelField) => {
  if (field.fieldType === 'STRING' && field.length !== null) return `STRING(${field.length})`;
  if (field.fieldType === 'DECIMAL') return `DECIMAL(${field.precision ?? '?'},${field.scale ?? '?'})`;
  return field.fieldType;
};

const modelCanvasColumns = (fields: readonly DataModelField[]): CanvasColumnSchema[] => (
  fields.map((field) => ({
    name: field.code,
    fieldType: field.fieldType,
    length: field.fieldType === 'STRING' ? field.length : null,
    precision: field.fieldType === 'DECIMAL' ? field.precision : null,
    scale: field.fieldType === 'DECIMAL' ? field.scale : null,
    nullable: field.nullable,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: field.description,
    geometry: field.geometry ?? null,
  }))
);

const modelUnavailableMessage = (
  detail: DataModelDetail | undefined,
  modelError: boolean,
) => {
  if (modelError) return '模型不存在或模型详情读取失败';
  if (!detail) return null;
  if (detail.model.status !== 'PUBLISHED') {
    return `模型当前状态为${dataModelStatusLabels[detail.model.status]}，只有已发布模型可用于 Canvas`;
  }
  return null;
};

const ModelMetadataCard = ({
  detail,
}: {
  detail: DataModelDetail;
}) => {
  const fields = sortedModelFields(detail);
  return (
    <Card size="small" title={`模型详情 · ${detail.model.name}`}>
      <Space orientation="vertical" size={10} className="canvas-inspector-content">
        <Descriptions size="small" column={1} colon={false}>
          <Descriptions.Item label="模型编码"><Typography.Text code>{detail.model.code}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="结构版本">v{detail.model.schemaVersion}</Descriptions.Item>
          <Descriptions.Item label="物理模式"><Tag>{physicalTableModeLabels[detail.model.physicalTableMode]}</Tag></Descriptions.Item>
          <Descriptions.Item label="数据源">{detail.model.storageDataSourceName}</Descriptions.Item>
          <Descriptions.Item label="物理表"><Typography.Text code>{detail.model.physicalTableName}</Typography.Text></Descriptions.Item>
        </Descriptions>
        <Table<DataModelField>
          size="small"
          rowKey="id"
          pagination={false}
          scroll={{ y: 220 }}
          dataSource={fields}
          columns={[
            { title: '字段编码', dataIndex: 'code', ellipsis: true },
            { title: '名称', dataIndex: 'name', ellipsis: true },
            { title: '平台类型', key: 'fieldType', width: 125, render: (_, field) => modelFieldTypeLabel(field) },
            { title: '可空', dataIndex: 'nullable', width: 54, render: (nullable: boolean) => nullable ? '是' : '否' },
            { title: '主键', dataIndex: 'primaryKey', width: 54, render: (primaryKey: boolean) => primaryKey ? '是' : '否' },
          ]}
        />
      </Space>
    </Card>
  );
};

interface ModelInputFormValues {
  modelId: string;
}

export const ModelInputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'MODEL_INPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<ModelInputFormValues>();
  const modelId = Form.useWatch('modelId', form) ?? '';
  const modelQuery = useDataModel(modelId || undefined, Boolean(modelId));
  const unavailableMessage = modelUnavailableMessage(
    modelQuery.data,
    modelQuery.isError,
  );

  const toConfiguration = (values: ModelInputFormValues): ModelInputConfiguration => ({
    models: values.modelId ? [{ modelId: values.modelId }] : [],
  });

  const submit = (values: ModelInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (modelQuery.isFetching && !modelQuery.data) {
          form.setFields([{ name: 'modelId', errors: ['正在读取模型信息，请稍候'] }]);

        }
        if (unavailableMessage) {
          form.setFields([{ name: 'modelId', errors: [unavailableMessage] }]);

        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      {unavailableMessage && <Alert showIcon type="error" title={unavailableMessage} />}
      <Form<ModelInputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={{ modelId: node.configuration.models[0]?.modelId ?? '' }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <Form.Item name="modelId" label="输入模型" rules={[{ required: true, message: '请选择输入模型' }]}>
          <CanvasModelSelect placeholder="选择已发布模型" />
        </Form.Item>
      </Form>
      {modelQuery.data && (
        <ModelMetadataCard detail={modelQuery.data} />
      )}
    </Space>
  );
};

interface ModelOutputFormValues {
  sourceTableName: string;
  targetModelId: string;
  writeMode: ModelOutputConfiguration['writeMode'];
  columnMappings: JdbcColumnMapping[];
}

export const ModelOutputInspector = ({
  node,
  executionMode = 'BATCH',
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
  splitLayout = false,
  hideNodeValidation = false,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'MODEL_OUTPUT' }>;
  executionMode?: CanvasExecutionMode;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
  splitLayout?: boolean;
  hideNodeValidation?: boolean;
}) => {
  const [form] = Form.useForm<ModelOutputFormValues>();
  const [modelDetailOpen, setModelDetailOpen] = useState(false);
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const targetModelId = Form.useWatch('targetModelId', form) ?? '';
  const writeMode = Form.useWatch('writeMode', form) ?? null;
  const sourceTable = validation?.inputTables.find((table) => table.name === sourceTableName);
  const modelQuery = useDataModel(targetModelId || undefined, Boolean(targetModelId));
  const storageDataSourceId = modelQuery.data?.model.storageDataSourceId;
  const storageDataSourceQuery = useDataSource(
    storageDataSourceId,
    Boolean(storageDataSourceId),
  );
  const targetDatabaseType = storageDataSourceQuery.data?.type;
  const modelMessage = modelUnavailableMessage(
    modelQuery.data,
    modelQuery.isError,
  );
  const overwriteExternal = modelQuery.data?.model.physicalTableMode === 'EXTERNAL' && writeMode === 'OVERWRITE';
  const targetFields = sortedModelFields(modelQuery.data);
  const targetColumns = modelCanvasColumns(targetFields);
  const primaryKeyColumns = targetFields
    .filter((field) => field.primaryKey)
    .map((field) => field.code);
  const upsertUnavailable = Boolean(modelQuery.data) && primaryKeyColumns.length === 0;
  const databaseModeReason = writeMode
    ? jdbcWriteModeUnavailableReason(targetDatabaseType, writeMode, executionMode)
    : null;
  const currentWriteModeUnavailableReason = databaseModeReason
    ?? (overwriteExternal ? 'EXTERNAL 模型不允许 OVERWRITE' : null)
    ?? (writeMode === 'UPSERT' && upsertUnavailable
      ? '目标模型必须定义主键才能使用 UPSERT'
      : null);
  const modelWriteModeOptions = (['APPEND', 'OVERWRITE', 'UPSERT'] as const).map((mode) => {
    const databaseReason = jdbcWriteModeUnavailableReason(
      targetDatabaseType,
      mode,
      executionMode,
    );
    const modelReason = databaseReason
      ?? (mode === 'OVERWRITE' && modelQuery.data?.model.physicalTableMode === 'EXTERNAL'
        ? 'EXTERNAL 模型不可用'
        : null)
      ?? (mode === 'UPSERT' && upsertUnavailable ? '目标模型未定义主键' : null);
    const action = mode === 'APPEND'
      ? '追加'
      : mode === 'OVERWRITE' ? '清空后写入' : '按模型主键插入或更新';
    return {
      value: mode,
      label: `${mode} · ${modelReason ?? action}`,
      disabled: Boolean(modelReason),
    };
  });

  const toConfiguration = (values: ModelOutputFormValues): ModelOutputConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    targetModelId: values.targetModelId ?? '',
    writeMode: values.writeMode ?? null,
    columnMappings: orderOutputFieldMappings(
      targetColumns,
      (values.columnMappings ?? []).map((mapping) => ({
        sourceColumnName: mapping.sourceColumnName ?? '',
        targetColumnName: mapping.targetColumnName ?? '',
      })),
    ),
    writes: [{
      writeId: node.configuration.writes?.[0]?.writeId ?? crypto.randomUUID(),
      sourceTableName: values.sourceTableName ?? '', targetModelId: values.targetModelId ?? '',
      writeMode: values.writeMode ?? null,
      columnMappings: orderOutputFieldMappings(targetColumns, (values.columnMappings ?? []).map((mapping) => ({ sourceColumnName: mapping.sourceColumnName ?? '', targetColumnName: mapping.targetColumnName ?? '' }))),
    }],
  });

  const submit = (values: ModelOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  const validateExternalState = () => {
    if (modelQuery.isFetching && !modelQuery.data) return '正在读取模型信息，请稍候';
    return modelMessage;
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        const currentModelMessage = validateExternalState();
        if (currentModelMessage) {
          form.setFields([{ name: 'targetModelId', errors: [currentModelMessage] }]);

        }
        if (modelQuery.data?.model.physicalTableMode === 'EXTERNAL' && values.writeMode === 'OVERWRITE') {
          form.setFields([{ name: 'writeMode', errors: ['EXTERNAL 模型不允许 OVERWRITE'] }]);

        }
        if (values.writeMode === 'UPSERT' && primaryKeyColumns.length === 0) {
          form.setFields([{ name: 'writeMode', errors: ['目标模型必须定义主键才能使用 UPSERT'] }]);

        }
        if (executionMode === 'STREAMING' && values.writeMode === 'OVERWRITE') {
          form.setFields([{ name: 'writeMode', errors: ['实时模型输出不支持 OVERWRITE'] }]);

        }
        if (values.writeMode) {
          const unavailableReason = jdbcWriteModeUnavailableReason(
            storageDataSourceQuery.data?.type,
            values.writeMode,
            executionMode,
          );
          if (unavailableReason) {
            form.setFields([{ name: 'writeMode', errors: [unavailableReason] }]);
          }
        }
        submit(values);
        return true;
      } catch (error) {
        focusFirstInvalidField(form, error);
        return false;
      }
    },
  }));

  const sourceOptions = retainedOption(
    (validation?.inputTables ?? []).map((table) => ({ value: table.name, label: table.name })),
    sourceTableName,
    `${sourceTableName}（上游已不可用）`,
  );
  return (
    <Space orientation="vertical" size={12} className={`canvas-inspector-content${splitLayout ? ' canvas-output-write-editor' : ''}`}>
      {!hideNodeValidation && <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
      {modelMessage && <Alert showIcon type="error" title={modelMessage} />}
      <Form<ModelOutputFormValues> autoComplete="off"
        form={form}
        layout="vertical"
        className={splitLayout ? 'canvas-output-write-editor-form' : undefined}
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          targetModelId: node.configuration.targetModelId,
          writeMode: node.configuration.writeMode,
          columnMappings: node.configuration.columnMappings,
        }}
        onFinish={submit}
        onValuesChange={(changed, values) => {
          if (changed.targetModelId !== undefined) setModelDetailOpen(false);
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <div className={splitLayout ? 'canvas-output-write-editor-grid' : undefined}>
        <div className={splitLayout ? 'canvas-output-write-editor-settings' : undefined}>
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true, message: '请选择来源表' }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={sourceOptions}
          />
        </Form.Item>
        <Form.Item label="目标模型" required>
          <div className="canvas-resource-select-with-action">
            <Form.Item name="targetModelId" noStyle rules={[{ required: true, message: '请选择目标模型' }]}>
              <CanvasModelSelect placeholder="选择已发布目标模型" />
            </Form.Item>
            <Tooltip title={modelQuery.data ? '查看目标模型详情' : '请先选择目标模型'}>
              <Button
                icon={<EyeOutlined />}
                aria-label="查看目标模型详情"
                disabled={!modelQuery.data}
                loading={modelQuery.isFetching && Boolean(targetModelId)}
                onClick={() => setModelDetailOpen(true)}
              />
            </Tooltip>
          </div>
        </Form.Item>
        <Form.Item
          name="writeMode"
          label={(
            <CanvasInspectorFieldLabel
              label="写入模式"
              tooltip={executionMode === 'STREAMING'
                ? '实时模型输出通过 foreachBatch 执行 APPEND 或 UPSERT，整体按至少一次交付。'
                : 'OVERWRITE 会先执行 TRUNCATE TABLE，再 APPEND 写入。两步不是同一原子事务；后续写入失败时，目标表可能为空或仅部分写入。'}
            />
          )}
          rules={[{ required: true, message: '请选择写入模式' }]}
          validateStatus={currentWriteModeUnavailableReason ? 'error' : undefined}
          help={currentWriteModeUnavailableReason ?? undefined}
        >
          <Select options={modelWriteModeOptions} />
        </Form.Item>
        {writeMode === 'UPSERT' && (
          <div className="canvas-compact-key-summary">
            <Typography.Text type="secondary">模型主键：</Typography.Text>
            {primaryKeyColumns.length > 0 ? (
              <Space size={[4, 4]} wrap>
                {primaryKeyColumns.map((column) => <Tag key={column}>{column}</Tag>)}
              </Space>
            ) : (
              <Typography.Text type="danger">目标模型未定义主键</Typography.Text>
            )}
          </div>
        )}
        </div>
        <div className={splitLayout ? 'canvas-output-write-editor-mappings' : undefined}>
        <OutputFieldMappingFields
          sourceColumns={sourceTable?.columns ?? []}
          targetColumns={targetColumns}
          targetFieldNames={new Map(targetFields.map((field) => [field.code, field.name]))}
          primaryKeyColumns={primaryKeyColumns}
          keyColumns={writeMode === 'UPSERT' ? primaryKeyColumns : []}
          keyLabel="UPSERT Key"
          initialMappings={node.configuration.columnMappings ?? []}
          sourceReady={Boolean(validation && sourceTable)}
          targetReady={Boolean(modelQuery.data)}
          targetLoading={modelQuery.isFetching}
          scrollHeight={splitLayout ? 520 : undefined}
          onProgrammaticChange={() => queueMicrotask(() => {
            const values = form.getFieldsValue(true);
            onDirtyChange(
              configurationFingerprint(toConfiguration(values))
                !== configurationFingerprint(node.configuration),
            );
          })}
        />
        </div>
        </div>
      </Form>
      <CanvasModelDetailModal
        detail={modelQuery.data}
        open={modelDetailOpen}
        onClose={() => setModelDetailOpen(false)}
      />
    </Space>
  );
};
