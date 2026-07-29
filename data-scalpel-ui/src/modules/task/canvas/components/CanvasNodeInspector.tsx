import { DownOutlined, DeleteOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Form, Input, Select, Space, Table, Tag, Typography } from 'antd';
import { forwardRef, useEffect, useImperativeHandle, useState, type Ref } from 'react';
import { useApiResource, useDataSource, useTableMetadata, type DataSource, type TableMetadata } from '../../../datasource';
import {
  dataModelStatusLabels,
  physicalTableModeLabels,
  useDataModel,
  usePhysicalTableInspection,
  type DataModelDetail,
  type DataModelField,
  type PhysicalTableInspection,
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
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type CanvasNodeConfiguration,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasExecutionMode,
  type CanvasNodeValidationResult,
  type JdbcColumnMapping,
  type JdbcInputConfiguration,
  type FileDatasetInputConfiguration,
  type FileOutputConfiguration,
  type HttpApiInputConfiguration,
  type JdbcOutputConfiguration,
  type JoinCondition,
  type JoinConfiguration,
  type KafkaInputConfiguration,
  type KafkaOutputConfiguration,
  type KafkaValueSchema,
  type ModelInputConfiguration,
  type ModelOutputConfiguration,
  type RenameConfiguration,
  type StreamJoinConfiguration,
  normalizeFileOutputPath,
} from '../canvasTypes';
import { CanvasJdbcDataSourceSelect, CanvasJdbcTableSelect } from './CanvasJdbcSelectors';
import { CanvasHttpApiDataSourceSelect, CanvasHttpApiResourceSelect } from './CanvasHttpApiSelectors';
import { CanvasModelSelect } from './CanvasModelSelect';
import { CanvasKafkaDataSourceSelect, CanvasKafkaTopicSelect } from './CanvasKafkaSelectors';
import { KafkaValueSchemaEditor } from './KafkaValueSchemaEditor';
import { CanvasS3DataSourceSelect } from './CanvasS3DataSourceSelect';

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

const configurationFingerprint = (configuration: CanvasNodeConfiguration): string => JSON.stringify(configuration);

const focusFirstInvalidField = (
  form: { scrollToField: (name: (string | number)[], options?: { focus?: boolean; block?: ScrollLogicalPosition }) => void },
  error: unknown,
) => {
  if (typeof error !== 'object' || error === null || !('errorFields' in error)) return;
  const firstError = (error as { errorFields?: { name: (string | number)[] }[] }).errorFields?.[0];
  if (firstError) form.scrollToField(firstError.name, { focus: true, block: 'center' });
};

const ValidationIssues = ({
  validation,
  unavailableMessage,
}: {
  validation: CanvasNodeValidationResult | undefined;
  unavailableMessage: string | null;
}) => {
  const [expanded, setExpanded] = useState(false);
  if (!validation && unavailableMessage) {
    return (
      <Alert
        showIcon
        type="info"
        title="Task Engine 尚未完成校验"
        description={unavailableMessage}
      />
    );
  }
  const errors = validation?.issues.filter((item) => item.severity === 'ERROR') ?? [];
  const warnings = validation?.issues.filter((item) => item.severity === 'WARNING') ?? [];
  if (errors.length === 0 && warnings.length === 0) return null;
  const issues = [...errors, ...warnings];
  const primaryIssue = issues.find((item) => item.code === 'REQUIRED_CONFIGURATION') ?? issues[0];
  const summary = [
    errors.length > 0 ? `${errors.length} 个错误` : '',
    warnings.length > 0 ? `${warnings.length} 个警告` : '',
  ].filter(Boolean).join(' · ');
  return (
    <Alert
      showIcon
      type={errors.length > 0 ? 'error' : 'warning'}
      className="canvas-validation-alert"
      title={(
        <div className="canvas-validation-title">
          <span className="canvas-validation-count">{summary}</span>
          <span className="canvas-validation-primary" title={primaryIssue.message}>{primaryIssue.message}</span>
        </div>
      )}
      action={(
        <Button
          type="text"
          size="small"
          className="canvas-validation-action"
          icon={expanded ? <UpOutlined /> : <DownOutlined />}
          aria-label={expanded ? '收起问题详情' : '展开问题详情'}
          aria-expanded={expanded}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? '收起' : '详情'}
        </Button>
      )}
      description={expanded ? (
        <div className="canvas-validation-list" role="list">
          {issues.map((item, index) => (
            <div className="canvas-validation-list-item" role="listitem" key={`${item.code}-${index}`}>
              <span className={`canvas-validation-dot canvas-validation-dot-${item.severity.toLowerCase()}`} />
              <div className="canvas-validation-issue">
                <span>{item.message}</span>
                <Typography.Text type="secondary" className="canvas-validation-code">{item.code}</Typography.Text>
              </div>
            </div>
          ))}
        </div>
      ) : undefined}
    />
  );
};

const FieldPreview = ({ columns, loading = false }: { columns: CanvasColumnSchema[]; loading?: boolean }) => (
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
  metadata?.columns.map((column) => ({
    name: column.name,
    fieldType: column.logicalType,
    length: column.length,
    precision: column.precision,
    scale: column.scale,
    nullable: column.nullable,
    defaultValue: column.defaultValue,
    autoIncrement: column.autoIncrement,
    generated: column.generated,
    comment: column.comment,
  })) ?? []
);

const qualifiedPhysicalTableName = (dataSource: DataSource | undefined, tableName: string) => {
  if (dataSource?.connection.kind !== 'JDBC') return tableName;
  return [dataSource.connection.databaseName, dataSource.connection.schemaName, tableName].filter(Boolean).join('.');
};

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

interface JdbcInputFormValues {
  dataSourceId: string;
  tableName?: string;
}

const JdbcInputInspector = ({
  node,
  validation,
  validationUnavailableMessage = null,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'JDBC_INPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<JdbcInputFormValues>();
  const selectedDataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const selectedTableName = Form.useWatch('tableName', form) ?? '';
  const selectedDataSourceQuery = useDataSource(selectedDataSourceId || undefined, Boolean(selectedDataSourceId));
  const selectedTableQuery = useTableMetadata(
    selectedDataSourceId || undefined,
    selectedTableName ? { catalog: null, schema: null, table: selectedTableName } : undefined,
    Boolean(selectedDataSourceId && selectedTableName),
  );
  const selectedDataSourceAvailable = selectedDataSourceQuery.data
    ? dataSourceAvailable(selectedDataSourceQuery.data, 'SOURCE')
    : selectedDataSourceQuery.isError ? false : undefined;
  const selectedTableAvailable = selectedTableQuery.data
    ? true
    : selectedTableQuery.isError ? false : undefined;
  const selectedColumns = canvasColumns(selectedTableQuery.data);

  const toConfiguration = (values: JdbcInputFormValues): JdbcInputConfiguration => ({
      dataSourceId: values.dataSourceId ?? '',
      tableName: values.tableName ?? '',
  });

  const submit = (values: JdbcInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && selectedDataSourceAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [selectedDataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '数据源不存在、已停用或不具有 SOURCE 用途'],
          }]);
          return false;
        }
        if (values.tableName && selectedTableAvailable !== true) {
          form.setFields([{
            name: 'tableName',
            errors: [selectedTableQuery.isFetching
              ? '正在读取物理表元数据，请稍候'
              : '该物理表不存在或不属于当前数据源'],
          }]);
          return false;
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
      <Form<JdbcInputFormValues>
        form={form}
        layout="vertical"
        initialValues={{
          dataSourceId: node.configuration.dataSourceId,
          tableName: node.configuration.tableName || undefined,
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <Form.Item
          name="dataSourceId"
          label="来源数据源"
          rules={[
            { required: true, message: '请选择来源数据源' },
            {
              validator: async () => {
                if (!selectedDataSourceId) return;
                if (selectedDataSourceQuery.isFetching && !selectedDataSourceQuery.data) {
                  throw new Error('正在读取数据源信息，请稍候');
                }
                if (selectedDataSourceAvailable === false) {
                  throw new Error('数据源不存在、已停用或不具有 SOURCE 用途');
                }
              },
            },
          ]}
        >
          <CanvasJdbcDataSourceSelect
            purpose="SOURCE"
            placeholder="选择 JDBC SOURCE 数据源"
          />
        </Form.Item>
        {selectedDataSourceId && selectedDataSourceQuery.isError && (
          <MetadataErrorAlert
            error={selectedDataSourceQuery.error}
            fallback="读取数据源信息失败"
            onRetry={() => void selectedDataSourceQuery.refetch()}
          />
        )}
        <Form.Item
          name="tableName"
          label="物理表"
          dependencies={['dataSourceId']}
          rules={[
            { required: true, message: '请选择物理表' },
            {
              validator: async () => {
                if (!selectedTableName) return;
                if (selectedTableQuery.isFetching && !selectedTableQuery.data) {
                  throw new Error('正在读取物理表元数据，请稍候');
                }
                if (selectedTableAvailable === false) {
                  throw new Error('该物理表不存在或不属于当前数据源');
                }
              },
            },
          ]}
        >
          <CanvasJdbcTableSelect
            dataSourceId={selectedDataSourceId}
            placeholder="输入表名搜索真实物理表"
            selectedTableAvailable={selectedTableAvailable}
          />
        </Form.Item>
        {selectedTableName && selectedTableQuery.isError && (
          <MetadataErrorAlert
            error={selectedTableQuery.error}
            fallback="读取物理表元数据失败"
            onRetry={() => void selectedTableQuery.refetch()}
          />
        )}
        {selectedTableName && (selectedTableQuery.isFetching || selectedTableQuery.data) && (
          <Card
            size="small"
            title={`字段 · ${qualifiedPhysicalTableName(selectedDataSourceQuery.data, selectedTableName)}`}
          >
            <FieldPreview columns={selectedColumns} loading={selectedTableQuery.isFetching} />
          </Card>
        )}
      </Form>
    </Space>
  );
};

interface FileDatasetInputFormValues {
  fileDatasetId: string;
  fileDatasetTableId: string;
}

const FileDatasetInputInspector = ({
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
    node.configuration.fileDatasetTableId ? [node.configuration.fileDatasetTableId] : [],
    Boolean(node.configuration.fileDatasetTableId),
  );
  const selectedMetadataQuery = useFileDatasetCanvasMetadata(
    selectedTableId ? [selectedTableId] : [],
    Boolean(selectedTableId),
  );
  const savedMetadata = savedMetadataQuery.data?.tables.find(
    (table) => table.fileDatasetTableId === node.configuration.fileDatasetTableId,
  );
  const selectedMetadata = selectedMetadataQuery.data?.tables.find(
    (table) => table.fileDatasetTableId === selectedTableId,
  );

  useEffect(() => {
    if (!savedMetadata || form.getFieldValue('fileDatasetId')) return;
    form.setFieldValue('fileDatasetId', savedMetadata.fileDatasetId);
  }, [form, savedMetadata]);

  const toConfiguration = (values: FileDatasetInputFormValues): FileDatasetInputConfiguration => ({
    fileDatasetTableId: values.fileDatasetTableId ?? '',
  });
  const submit = (values: FileDatasetInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        submit(await form.validateFields());
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
    disabled: table.parseStatus !== 'READY',
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
      fieldType: field.fieldType,
      length: field.length,
      precision: field.precision,
      scale: field.scale,
      nullable: field.nullable,
      defaultValue: null,
      autoIncrement: false,
      generated: false,
      comment: null,
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
      <Form<FileDatasetInputFormValues>
        form={form}
        layout="vertical"
        initialValues={{
          fileDatasetId: savedMetadata?.fileDatasetId ?? '',
          fileDatasetTableId: node.configuration.fileDatasetTableId,
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
          extra="只有 READY 表可以新选；已保存表失效时会保留原 ID 并显示 Compiler 错误。"
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

const HttpApiInputInspector = ({
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
  })) ?? [];
  const toConfiguration = (values: HttpApiInputFormValues): HttpApiInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '',
    resourceId: values.resourceId ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    runtimeParameters: (values.runtimeParameters ?? []).map((parameter) => ({
      name: parameter.name?.trim() ?? '',
      value: parameter.value ?? '',
    })),
  });
  const submit = (values: HttpApiInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };
  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && sourceAvailable !== true) {
          form.setFields([{ name: 'dataSourceId', errors: [dataSourceQuery.isFetching ? '正在读取数据源，请稍候' : 'HTTP API 数据源不存在、停用或不具有 SOURCE 用途'] }]);
          return false;
        }
        if (values.resourceId && resourceAvailable !== true) {
          form.setFields([{ name: 'resourceId', errors: [resourceQuery.isFetching ? '正在读取 API 资源，请稍候' : 'API 资源不存在或已停用'] }]);
          return false;
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
    <Form<HttpApiInputFormValues>
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
}

const KafkaInputInspector = ({
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
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceValid = dataSourceQuery.data
    ? kafkaDataSourceAvailable(dataSourceQuery.data, 'SOURCE')
    : dataSourceQuery.isError ? false : undefined;
  const toConfiguration = (values: KafkaInputFormValues): KafkaInputConfiguration => ({
    dataSourceId: values.dataSourceId ?? '',
    topic: values.topic?.trim() ?? '',
    valueSchema: values.valueSchema ?? { columns: [] },
    outputTableName: values.outputTableName?.trim() ?? '',
    startingOffsets: values.startingOffsets ?? null,
  });
  const submit = (values: KafkaInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && dataSourceValid !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取 Kafka 数据源，请稍候'
              : 'Kafka 数据源不存在、已停用或不具有 SOURCE 用途'],
          }]);
          return false;
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
      <Form<KafkaInputFormValues>
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
        <Form.Item name="dataSourceId" label="Kafka 数据源" rules={[{ required: true }]}>
          <CanvasKafkaDataSourceSelect purpose="SOURCE" placeholder="选择 Kafka 来源" />
        </Form.Item>
        <Form.Item name="topic" label="输入 Topic" rules={[{ required: true, whitespace: true }]}>
          <CanvasKafkaTopicSelect dataSourceId={dataSourceId} placeholder="搜索并选择 Topic" />
        </Form.Item>
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
}

const JoinInspector = ({
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
  });

  const submit = (values: JoinFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        submit(await form.validateFields());
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
      <Form<JoinFormValues>
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
}

const StreamJoinInspector = ({
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
  });
  const submit = (values: StreamJoinFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        submit(await form.validateFields());
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
      <Form<StreamJoinFormValues>
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
      </Form>
    </Space>
  );
};

interface RenameFormValues {
  sourceTableName: string;
  outputTableName: string;
  columnMappings: JdbcColumnMapping[];
}

const RenameInspector = ({
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
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const tableOptions = (validation?.inputTables ?? [])
    .map((table) => ({ value: table.name, label: table.name }));

  const toConfiguration = (values: RenameFormValues): RenameConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    columnMappings: (values.columnMappings ?? []).map((mapping) => ({
      sourceColumnName: mapping.sourceColumnName ?? '',
      targetColumnName: mapping.targetColumnName ?? '',
    })),
  });

  const submit = (values: RenameFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        submit(await form.validateFields());
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
      <Form<RenameFormValues>
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
        <Typography.Text strong>字段重命名</Typography.Text>
        <Typography.Text type="secondary">
          所有映射同时生效，支持 a→b、b→a 交换名称。
        </Typography.Text>
        <Form.List name="columnMappings">
          {(fields, { add, remove }) => (
            <Space orientation="vertical" size={8} className="canvas-condition-list">
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  size="small"
                  title={`字段 ${index + 1}`}
                  extra={(
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除字段映射 ${index + 1}`}
                      onClick={() => remove(field.name)}
                    />
                  )}
                >
                  <Form.Item
                    name={[field.name, 'sourceColumnName']}
                    rules={[{ required: true, message: '请选择来源字段' }]}
                  >
                    <Select
                      disabled={!validation || !source}
                      placeholder="来源字段"
                      options={source?.columns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${platformTypeLabel(column)}`,
                      })) ?? []}
                    />
                  </Form.Item>
                  <div className="canvas-join-operator">→</div>
                  <Form.Item
                    name={[field.name, 'targetColumnName']}
                    rules={[{ required: true, whitespace: true, message: '请输入目标字段名' }]}
                  >
                    <Input placeholder="目标字段名" />
                  </Form.Item>
                </Card>
              ))}
              <Button
                icon={<PlusOutlined />}
                disabled={!source}
                onClick={() => add({ sourceColumnName: '', targetColumnName: '' })}
              >
                添加字段重命名
              </Button>
            </Space>
          )}
        </Form.List>
      </Form>
      {source && (
        <Card size="small" title={`来源字段 · ${source.name}`}>
          <FieldPreview columns={source.columns} />
        </Card>
      )}
    </Space>
  );
};

interface JdbcOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName?: string;
  writeMode: JdbcOutputConfiguration['writeMode'];
  columnMappingMode: JdbcOutputConfiguration['columnMappingMode'];
  columnMappings: JdbcColumnMapping[];
}

const JdbcOutputInspector = ({
  node,
  executionMode = 'BATCH',
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'JDBC_OUTPUT' }>;
  executionMode: CanvasExecutionMode;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<JdbcOutputFormValues>();
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const selectedDataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const selectedTableName = Form.useWatch('targetTableName', form) ?? '';
  const mappingMode = Form.useWatch('columnMappingMode', form) ?? null;
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

  const toConfiguration = (values: JdbcOutputFormValues): JdbcOutputConfiguration => ({
      sourceTableName: values.sourceTableName ?? '',
      dataSourceId: values.dataSourceId ?? '',
      targetTableName: values.targetTableName ?? '',
      writeMode: values.writeMode ?? null,
      columnMappingMode: values.columnMappingMode ?? null,
      columnMappings: values.columnMappingMode === 'EXPLICIT'
        ? (values.columnMappings ?? []).map((mapping) => ({
          sourceColumnName: mapping.sourceColumnName ?? '',
          targetColumnName: mapping.targetColumnName ?? '',
        }))
        : [],
  });

  const submit = (values: JdbcOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && selectedDataSourceAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [selectedDataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '数据源不存在、已停用或不具有数据分发用途'],
          }]);
          return false;
        }
        if (values.targetTableName && selectedTableAvailable !== true) {
          form.setFields([{
            name: 'targetTableName',
            errors: [selectedTableQuery.isFetching
              ? '正在读取目标表元数据，请稍候'
              : '该目标表不存在或不属于当前数据源'],
          }]);
          return false;
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
      <Form<JdbcOutputFormValues>
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          dataSourceId: node.configuration.dataSourceId,
          targetTableName: node.configuration.targetTableName || undefined,
          writeMode: node.configuration.writeMode,
          columnMappingMode: node.configuration.columnMappingMode,
          columnMappings: node.configuration.columnMappings,
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={(validation?.inputTables ?? []).map((table) => ({ value: table.name, label: table.name }))}
          />
        </Form.Item>
        <Form.Item
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
        </Form.Item>
        {selectedDataSourceId && selectedDataSourceQuery.isError && (
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
          label="写入模式"
          rules={[
            { required: true },
            {
              validator: (_, value) => executionMode === 'STREAMING' && value !== 'APPEND'
                ? Promise.reject(new Error('实时 JDBC 输出仅支持 APPEND'))
                : Promise.resolve(),
            },
          ]}
          extra={executionMode === 'STREAMING'
            ? '实时任务通过 foreachBatch 追加写入，按至少一次处理。'
            : undefined}
        >
          <Select options={executionMode === 'STREAMING'
            ? [
              { value: 'APPEND', label: 'APPEND · 追加' },
              ...(node.configuration.writeMode === 'OVERWRITE'
                ? [{ value: 'OVERWRITE', label: 'OVERWRITE · 实时模式不支持', disabled: true }]
                : []),
            ]
            : [
              { value: 'APPEND', label: 'APPEND · 追加' },
              { value: 'OVERWRITE', label: 'OVERWRITE · 清空后写入' },
            ]} />
        </Form.Item>
        <Form.Item name="columnMappingMode" label="字段映射模式" rules={[{ required: true }]}>
          <Select options={[{ value: 'BY_NAME', label: 'BY_NAME · 同名自动映射' }, { value: 'EXPLICIT', label: 'EXPLICIT · 显式映射' }]} />
        </Form.Item>
        {mappingMode === 'BY_NAME' && (
          <Alert
            showIcon
            type="info"
            title="BY_NAME 映射由 Task Engine 校验"
            description="应用配置后，Task Engine 会按照同名字段生成映射并检查必填字段与类型兼容性。"
          />
        )}
        {mappingMode === 'EXPLICIT' && (
          <Form.List name="columnMappings">
            {(fields, { add, remove }) => (
              <Space orientation="vertical" size={8} className="canvas-condition-list">
                {fields.map((field, index) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`字段映射 ${index + 1}`}
                    extra={<Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除字段映射 ${index + 1}`} onClick={() => remove(field.name)} />}
                  >
                    <Form.Item name={[field.name, 'sourceColumnName']} rules={[{ required: true }]}>
                      <Select disabled={!validation} placeholder="来源字段" options={source?.columns.map((column) => ({ value: column.name, label: column.name })) ?? []} />
                    </Form.Item>
                    <div className="canvas-join-operator">→</div>
                    <Form.Item name={[field.name, 'targetColumnName']} rules={[{ required: true }]}>
                      <Select placeholder="目标字段" options={selectedTargetColumns.map((column) => ({ value: column.name, label: column.name }))} />
                    </Form.Item>
                  </Card>
                ))}
                <Button icon={<PlusOutlined />} onClick={() => add({ sourceColumnName: '', targetColumnName: '' })}>
                  添加字段映射
                </Button>
              </Space>
            )}
          </Form.List>
        )}
      </Form>
    </Space>
  );
};

interface KafkaOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  keyColumnName?: string;
  columnMappingMode: KafkaOutputConfiguration['columnMappingMode'];
  columnMappings: JdbcColumnMapping[];
}

const KafkaOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'KAFKA_OUTPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<KafkaOutputFormValues>();
  const sourceName = Form.useWatch('sourceTableName', form) ?? '';
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const valueSchema = Form.useWatch('valueSchema', form) ?? { columns: [] };
  const mappingMode = Form.useWatch('columnMappingMode', form) ?? null;
  const source = validation?.inputTables.find((table) => table.name === sourceName);
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const dataSourceValid = dataSourceQuery.data
    ? kafkaDataSourceAvailable(dataSourceQuery.data, 'DISTRIBUTION')
    : dataSourceQuery.isError ? false : undefined;
  const targetFields = valueSchema.columns;
  const toConfiguration = (values: KafkaOutputFormValues): KafkaOutputConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    dataSourceId: values.dataSourceId ?? '',
    topic: values.topic?.trim() ?? '',
    valueSchema: values.valueSchema ?? { columns: [] },
    keyColumnName: values.keyColumnName ?? '',
    columnMappingMode: values.columnMappingMode ?? null,
    columnMappings: values.columnMappingMode === 'EXPLICIT'
      ? (values.columnMappings ?? []).map((mapping) => ({
        sourceColumnName: mapping.sourceColumnName ?? '',
        targetColumnName: mapping.targetColumnName ?? '',
      }))
      : [],
  });
  const submit = (values: KafkaOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && dataSourceValid !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取 Kafka 数据源，请稍候'
              : 'Kafka 数据源不存在、已停用或不具有 DISTRIBUTION 用途'],
          }]);
          return false;
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
      <Alert
        type="warning"
        showIcon
        title="Kafka Sink 按至少一次处理"
        description="故障恢复可能重复发送消息；多个输出没有跨 Sink 事务。"
      />
      <Form<KafkaOutputFormValues>
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
        <Form.Item name="sourceTableName" label="来源流表" rules={[{ required: true }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择无界上游流表' : '等待 Task Engine 计算上游表'}
            options={(validation?.inputTables ?? [])
              .filter((table) => table.datasetKind === 'UNBOUNDED')
              .map((table) => ({ value: table.name, label: table.name }))}
          />
        </Form.Item>
        <Form.Item name="dataSourceId" label="Kafka 数据源" rules={[{ required: true }]}>
          <CanvasKafkaDataSourceSelect purpose="DISTRIBUTION" placeholder="选择 Kafka 输出" />
        </Form.Item>
        <Form.Item name="topic" label="输出 Topic" rules={[{ required: true, whitespace: true }]}>
          <CanvasKafkaTopicSelect dataSourceId={dataSourceId} placeholder="搜索并选择 Topic" />
        </Form.Item>
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
        <Form.Item name="keyColumnName" label="Key 字段（可选）">
          <Select
            allowClear
            disabled={!source}
            placeholder="不设置时 Kafka Key 为空"
            options={source?.columns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${platformTypeLabel(column)}`,
            })) ?? []}
          />
        </Form.Item>
        <Form.Item name="columnMappingMode" label="Value 字段映射" rules={[{ required: true }]}>
          <Select options={[
            { value: 'BY_NAME', label: 'BY_NAME · 同名自动映射' },
            { value: 'EXPLICIT', label: 'EXPLICIT · 显式映射' },
          ]} />
        </Form.Item>
        {mappingMode === 'EXPLICIT' && (
          <Form.List name="columnMappings">
            {(fields, { add, remove }) => (
              <Space orientation="vertical" size={8} className="canvas-condition-list">
                {fields.map((field, index) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`字段映射 ${index + 1}`}
                    extra={(
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`删除 Kafka 字段映射 ${index + 1}`}
                        onClick={() => remove(field.name)}
                      />
                    )}
                  >
                    <Form.Item name={[field.name, 'sourceColumnName']} rules={[{ required: true }]}>
                      <Select
                        placeholder="来源字段"
                        options={source?.columns.map((column) => ({
                          value: column.name,
                          label: column.name,
                        })) ?? []}
                      />
                    </Form.Item>
                    <div className="canvas-join-operator">→</div>
                    <Form.Item name={[field.name, 'targetColumnName']} rules={[{ required: true }]}>
                      <Select
                        placeholder="Value Schema 字段"
                        options={targetFields.map((field) => ({
                          value: field.name,
                          label: `${field.name} · ${field.fieldType}`,
                        }))}
                      />
                    </Form.Item>
                  </Card>
                ))}
                <Button
                  icon={<PlusOutlined />}
                  onClick={() => add({ sourceColumnName: '', targetColumnName: '' })}
                >
                  添加字段映射
                </Button>
              </Space>
            )}
          </Form.List>
        )}
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

const physicalLocation = (detail: DataModelDetail) => [
  detail.model.catalogName,
  detail.model.schemaName,
  detail.model.physicalTableName,
].filter(Boolean).join('.');

const modelUnavailableMessage = (
  detail: DataModelDetail | undefined,
  modelError: boolean,
  inspection: PhysicalTableInspection | undefined,
  inspectionError: boolean,
) => {
  if (modelError) return '模型不存在或模型详情读取失败';
  if (!detail) return null;
  if (detail.model.status !== 'PUBLISHED') {
    return `模型当前状态为${dataModelStatusLabels[detail.model.status]}，只有已发布模型可用于 Canvas`;
  }
  if (inspectionError) return '模型物理表检查失败';
  if (inspection && !inspection.compatible) return inspection.message || '模型定义与物理表结构不一致';
  return null;
};

const ModelMetadataCard = ({
  detail,
  inspection,
  loading,
}: {
  detail: DataModelDetail;
  inspection: PhysicalTableInspection | undefined;
  loading: boolean;
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
          <Descriptions.Item label="物理位置"><Typography.Text code>{physicalLocation(detail)}</Typography.Text></Descriptions.Item>
          {inspection && <Descriptions.Item label="物理检查">{inspection.message}</Descriptions.Item>}
        </Descriptions>
        <Table<DataModelField>
          size="small"
          rowKey="id"
          pagination={false}
          loading={loading}
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

const ModelInputInspector = ({
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
  const inspectionQuery = usePhysicalTableInspection(
    modelId || undefined,
    Boolean(modelId && modelQuery.data),
  );
  const unavailableMessage = modelUnavailableMessage(
    modelQuery.data,
    modelQuery.isError,
    inspectionQuery.data,
    inspectionQuery.isError,
  );

  const toConfiguration = (values: ModelInputFormValues): ModelInputConfiguration => ({
    modelId: values.modelId ?? '',
  });

  const submit = (values: ModelInputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (modelQuery.isFetching && !modelQuery.data) {
          form.setFields([{ name: 'modelId', errors: ['正在读取模型信息，请稍候'] }]);
          return false;
        }
        if (inspectionQuery.isFetching && !inspectionQuery.data) {
          form.setFields([{ name: 'modelId', errors: ['正在检查模型物理表，请稍候'] }]);
          return false;
        }
        if (unavailableMessage) {
          form.setFields([{ name: 'modelId', errors: [unavailableMessage] }]);
          return false;
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
      <Form<ModelInputFormValues>
        form={form}
        layout="vertical"
        initialValues={{ modelId: node.configuration.modelId }}
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
        <ModelMetadataCard
          detail={modelQuery.data}
          inspection={inspectionQuery.data}
          loading={inspectionQuery.isFetching}
        />
      )}
    </Space>
  );
};

interface ModelOutputFormValues {
  sourceTableName: string;
  targetModelId: string;
  writeMode: ModelOutputConfiguration['writeMode'];
  columnMappingMode: ModelOutputConfiguration['columnMappingMode'];
  columnMappings: JdbcColumnMapping[];
}

const ModelOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'MODEL_OUTPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<ModelOutputFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const targetModelId = Form.useWatch('targetModelId', form) ?? '';
  const writeMode = Form.useWatch('writeMode', form) ?? null;
  const mappingMode = Form.useWatch('columnMappingMode', form) ?? null;
  const mappingValues = Form.useWatch('columnMappings', form) ?? [];
  const sourceTable = validation?.inputTables.find((table) => table.name === sourceTableName);
  const modelQuery = useDataModel(targetModelId || undefined, Boolean(targetModelId));
  const inspectionQuery = usePhysicalTableInspection(
    targetModelId || undefined,
    Boolean(targetModelId && modelQuery.data),
  );
  const modelMessage = modelUnavailableMessage(
    modelQuery.data,
    modelQuery.isError,
    inspectionQuery.data,
    inspectionQuery.isError,
  );
  const overwriteExternal = modelQuery.data?.model.physicalTableMode === 'EXTERNAL' && writeMode === 'OVERWRITE';
  const targetFields = sortedModelFields(modelQuery.data);

  const toConfiguration = (values: ModelOutputFormValues): ModelOutputConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    targetModelId: values.targetModelId ?? '',
    writeMode: values.writeMode ?? null,
    columnMappingMode: values.columnMappingMode ?? null,
    columnMappings: values.columnMappingMode === 'EXPLICIT'
      ? (values.columnMappings ?? []).map((mapping) => ({
        sourceColumnName: mapping.sourceColumnName ?? '',
        targetColumnName: mapping.targetColumnName ?? '',
      }))
      : [],
  });

  const submit = (values: ModelOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  const validateExternalState = () => {
    if (modelQuery.isFetching && !modelQuery.data) return '正在读取模型信息，请稍候';
    if (inspectionQuery.isFetching && !inspectionQuery.data) return '正在检查模型物理表，请稍候';
    return modelMessage;
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        const currentModelMessage = validateExternalState();
        if (currentModelMessage) {
          form.setFields([{ name: 'targetModelId', errors: [currentModelMessage] }]);
          return false;
        }
        if (modelQuery.data?.model.physicalTableMode === 'EXTERNAL' && values.writeMode === 'OVERWRITE') {
          form.setFields([{ name: 'writeMode', errors: ['EXTERNAL 模型不允许 OVERWRITE'] }]);
          return false;
        }
        if (values.columnMappingMode === 'EXPLICIT' && (values.columnMappings?.length ?? 0) === 0) {
          form.setFields([{ name: 'columnMappings', errors: ['EXPLICIT 模式至少需要一条字段映射'] }]);
          return false;
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
  const sourceColumnOptions = (mappingValue: string) => retainedOption(
    (sourceTable?.columns ?? []).map((column) => ({ value: column.name, label: column.name })),
    mappingValue,
    `${mappingValue}（来源字段已不可用）`,
  );
  const targetColumnOptions = (mappingValue: string) => retainedOption(
    targetFields.map((field) => ({ value: field.code, label: `${field.code} · ${field.name}` })),
    mappingValue,
    `${mappingValue}（目标字段已不可用）`,
  );
  const exactMappings = () => {
    const targetCodes = new Set(targetFields.map((field) => field.code));
    return (sourceTable?.columns ?? [])
      .filter((column) => targetCodes.has(column.name))
      .map((column) => ({ sourceColumnName: column.name, targetColumnName: column.name }));
  };
  const applyExactMappings = () => {
    const columnMappings = exactMappings();
    form.setFieldValue('columnMappings', columnMappings);
    const values = { ...form.getFieldsValue(true), columnMappings };
    onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <ValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      {modelMessage && <Alert showIcon type="error" title={modelMessage} />}
      {overwriteExternal && <Alert showIcon type="error" title="EXTERNAL 模型不允许 OVERWRITE" />}
      <Form<ModelOutputFormValues>
        form={form}
        layout="vertical"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          targetModelId: node.configuration.targetModelId,
          writeMode: node.configuration.writeMode,
          columnMappingMode: node.configuration.columnMappingMode,
          columnMappings: node.configuration.columnMappings,
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(configurationFingerprint(toConfiguration(values)) !== configurationFingerprint(node.configuration));
        }}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true, message: '请选择来源表' }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={sourceOptions}
          />
        </Form.Item>
        <Form.Item name="targetModelId" label="目标模型" rules={[{ required: true, message: '请选择目标模型' }]}>
          <CanvasModelSelect placeholder="选择已发布目标模型" />
        </Form.Item>
        <Form.Item name="writeMode" label="写入模式" rules={[{ required: true, message: '请选择写入模式' }]}>
          <Select options={[
            { value: 'APPEND', label: 'APPEND · 追加' },
            {
              value: 'OVERWRITE',
              label: modelQuery.data?.model.physicalTableMode === 'EXTERNAL'
                ? 'OVERWRITE · EXTERNAL 模型不可用'
                : 'OVERWRITE · 清空后写入',
              disabled: modelQuery.data?.model.physicalTableMode === 'EXTERNAL',
            },
          ]} />
        </Form.Item>
        <Form.Item name="columnMappingMode" label="字段映射模式" rules={[{ required: true, message: '请选择字段映射模式' }]}>
          <Select options={[
            { value: 'BY_NAME', label: 'BY_NAME · 精确同名映射' },
            { value: 'EXPLICIT', label: 'EXPLICIT · 显式映射' },
          ]} />
        </Form.Item>
        {mappingMode === 'BY_NAME' && (
          <Alert
            showIcon
            type="info"
            title="BY_NAME 映射由 Task Engine 校验"
            description="按照来源字段名与模型字段 code 精确匹配，不忽略大小写、横线或下划线。"
          />
        )}
        {mappingMode === 'EXPLICIT' && (
          <Form.List
            name="columnMappings"
            rules={[{
              validator: async (_, mappings: JdbcColumnMapping[] | undefined) => {
                if ((mappings?.length ?? 0) === 0) throw new Error('EXPLICIT 模式至少需要一条字段映射');
              },
            }]}
          >
              {(fields, { add, remove }, { errors }) => (
                <Space orientation="vertical" size={8} className="canvas-condition-list">
                  <Button
                    onClick={applyExactMappings}
                    disabled={!sourceTable || targetFields.length === 0}
                  >
                    精确同名匹配
                  </Button>
                  {fields.map((field, index) => {
                    const mapping = mappingValues[index] ?? node.configuration.columnMappings[index];
                    return (
                      <Card
                        key={field.key}
                        size="small"
                        title={`字段映射 ${index + 1}`}
                        extra={<Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除模型字段映射 ${index + 1}`} onClick={() => remove(field.name)} />}
                      >
                        <Form.Item name={[field.name, 'sourceColumnName']} rules={[{ required: true, message: '请选择来源字段' }]}>
                          <Select
                            aria-label={`来源字段映射 ${index + 1}`}
                            disabled={!validation}
                            placeholder="来源字段"
                            options={sourceColumnOptions(mapping?.sourceColumnName ?? '')}
                          />
                        </Form.Item>
                        <div className="canvas-join-operator">→</div>
                        <Form.Item name={[field.name, 'targetColumnName']} rules={[{ required: true, message: '请选择目标字段' }]}>
                          <Select
                            aria-label={`目标字段映射 ${index + 1}`}
                            placeholder="目标模型字段"
                            options={targetColumnOptions(mapping?.targetColumnName ?? '')}
                          />
                        </Form.Item>
                      </Card>
                    );
                  })}
                  <Button icon={<PlusOutlined />} onClick={() => add({ sourceColumnName: '', targetColumnName: '' })}>
                    添加模型字段映射
                  </Button>
                  <Form.ErrorList errors={errors} />
                </Space>
              )}
          </Form.List>
        )}
      </Form>
      {modelQuery.data && (
        <ModelMetadataCard
          detail={modelQuery.data}
          inspection={inspectionQuery.data}
          loading={inspectionQuery.isFetching}
        />
      )}
    </Space>
  );
};

interface FileOutputFormValues {
  sourceTableName: string;
  dataSourceId: string;
  targetPath: string;
  conflictPolicy: FileOutputConfiguration['conflictPolicy'];
  formatType: FileOutputConfiguration['formatOptions']['type'];
  header: boolean;
  delimiter: string;
  quote: string;
  escape: string;
  nullValue: string;
  ignoreNullFields: boolean;
}

const fileOutputConfiguration = (values: Partial<FileOutputFormValues>): FileOutputConfiguration => {
  const type = values.formatType ?? 'CSV';
  const formatOptions: FileOutputConfiguration['formatOptions'] = type === 'CSV'
    ? {
      type,
      header: values.header ?? true,
      delimiter: values.delimiter ?? ',',
      quote: values.quote ?? '"',
      escape: values.escape ?? '\\',
      nullValue: values.nullValue ?? '',
    }
    : type === 'JSON_LINES'
      ? { type, ignoreNullFields: values.ignoreNullFields ?? false }
      : { type: 'PARQUET' };
  return {
    sourceTableName: values.sourceTableName ?? '',
    dataSourceId: values.dataSourceId ?? '',
    targetPath: normalizeFileOutputPath(values.targetPath ?? ''),
    conflictPolicy: values.conflictPolicy ?? 'FAIL_IF_EXISTS',
    formatOptions,
  };
};

const fileOutputInitialValues = (
  configuration: FileOutputConfiguration,
): FileOutputFormValues => ({
  sourceTableName: configuration.sourceTableName,
  dataSourceId: configuration.dataSourceId,
  targetPath: configuration.targetPath,
  conflictPolicy: configuration.conflictPolicy,
  formatType: configuration.formatOptions.type,
  header: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.header : true,
  delimiter: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.delimiter : ',',
  quote: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.quote : '"',
  escape: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.escape : '\\',
  nullValue: configuration.formatOptions.type === 'CSV' ? configuration.formatOptions.nullValue : '',
  ignoreNullFields: configuration.formatOptions.type === 'JSON_LINES'
    ? configuration.formatOptions.ignoreNullFields : false,
});

const validateFileOutputPath = async (_: unknown, value: string | undefined) => {
  if (!value?.trim()) return;
  const normalized = normalizeFileOutputPath(value);
  if (normalized.length > 1024 || normalized.startsWith('/') || normalized.includes('\\')
      || normalized.includes('://') || normalized.includes('?') || normalized.includes('#')) {
    throw new Error('请输入合法的 S3 相对目录');
  }
  if (normalized.split('/').some((segment) => (
    !segment || segment === '.' || segment === '..' || segment.toLowerCase() === '_temporary'
  ))) {
    throw new Error('目录不能包含空段、.、.. 或 _temporary');
  }
};

const FileOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: {
  node: Extract<CanvasNodeDefinition, { type: 'FILE_OUTPUT' }>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: CanvasNodeInspectorProps['onApply'];
  onDirtyChange: CanvasNodeInspectorProps['onDirtyChange'];
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}) => {
  const [form] = Form.useForm<FileOutputFormValues>();
  const selectedDataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const targetPath = Form.useWatch('targetPath', form) ?? '';
  const formatType = Form.useWatch('formatType', form) ?? 'CSV';
  const conflictPolicy = Form.useWatch('conflictPolicy', form) ?? 'FAIL_IF_EXISTS';
  const selectedDataSourceQuery = useDataSource(
    selectedDataSourceId || undefined,
    Boolean(selectedDataSourceId),
  );
  const selectedDataSource = selectedDataSourceQuery.data;
  const selectedAvailable = selectedDataSource
    ? selectedDataSource.enabled
      && selectedDataSource.connectionKind === 'S3'
      && selectedDataSource.connection.kind === 'S3'
      && selectedDataSource.purposes.includes('DISTRIBUTION')
    : selectedDataSourceQuery.isError ? false : undefined;
  const normalizedPath = normalizeFileOutputPath(targetPath);
  const targetPreview = selectedDataSource?.connection.kind === 'S3' && normalizedPath
    ? `s3a://${selectedDataSource.connection.bucket}/${
      [selectedDataSource.connection.rootPrefix, normalizedPath].filter(Boolean).join('/')
    }/`
    : null;
  const submit = (values: FileOutputFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: fileOutputConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = await form.validateFields();
        if (values.dataSourceId && selectedAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [selectedDataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '数据源不存在、已停用、不是 S3 或不具有数据分发用途'],
          }]);
          return false;
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
      <Form<FileOutputFormValues>
        form={form}
        layout="vertical"
        initialValues={fileOutputInitialValues(node.configuration)}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            configurationFingerprint(fileOutputConfiguration(values))
              !== configurationFingerprint(node.configuration),
          );
        }}
      >
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true, message: '请选择来源表' }]}>
          <Select
            disabled={!validation}
            placeholder={validation ? '选择上游表' : '等待 Task Engine 计算上游表'}
            options={(validation?.inputTables ?? []).map((table) => ({
              value: table.name,
              label: table.name,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="dataSourceId"
          label="目标数据源"
          rules={[{ required: true, message: '请选择 S3 数据分发数据源' }]}
        >
          <CanvasS3DataSourceSelect placeholder="选择 S3 数据分发数据源" />
        </Form.Item>
        <Form.Item
          name="targetPath"
          label="目标目录"
          extra="相对于数据源根目录；不会自动追加任务 ID 或运行 ID。"
          rules={[
            { required: true, message: '请输入目标目录' },
            { validator: validateFileOutputPath },
          ]}
        >
          <Input placeholder="例如 exports/users" maxLength={1024} />
        </Form.Item>
        {targetPreview && (
          <Typography.Text type="secondary" code copyable>{targetPreview}</Typography.Text>
        )}
        <Form.Item name="conflictPolicy" label="目录冲突策略" rules={[{ required: true }]}>
          <Select options={[
            { value: 'FAIL_IF_EXISTS', label: 'FAIL_IF_EXISTS · 已存在则失败' },
            { value: 'OVERWRITE', label: 'OVERWRITE · 删除旧目录后写入' },
          ]} />
        </Form.Item>
        <Form.Item name="formatType" label="文件格式" rules={[{ required: true }]}>
          <Select options={[
            { value: 'CSV', label: 'CSV' },
            { value: 'JSON_LINES', label: 'JSON Lines · 每行一个对象' },
            { value: 'PARQUET', label: 'Parquet · Snappy' },
          ]} />
        </Form.Item>
        {formatType === 'CSV' && (
          <>
            <Form.Item name="header" label="输出表头">
              <Select options={[
                { value: true, label: '是' },
                { value: false, label: '否' },
              ]} />
            </Form.Item>
            {([
              ['delimiter', '分隔符'],
              ['quote', '引用符'],
              ['escape', '转义符'],
            ] as const).map(([name, label]) => (
              <Form.Item
                key={name}
                name={name}
                label={label}
                rules={[{
                  validator: async (_, value: string | undefined) => {
                    if (!value || [...value].length !== 1 || /[\r\n]/.test(value)) {
                      throw new Error(`${label}必须是一个非换行字符`);
                    }
                  },
                }]}
              >
                <Input maxLength={2} />
              </Form.Item>
            ))}
            <Form.Item name="nullValue" label="空值文本">
              <Input placeholder="默认输出为空字符串" />
            </Form.Item>
          </>
        )}
        {formatType === 'JSON_LINES' && (
          <Form.Item name="ignoreNullFields" label="忽略空值字段">
            <Select options={[
              { value: false, label: '否 · 保留所有字段' },
              { value: true, label: '是 · 省略 null 字段' },
            ]} />
          </Form.Item>
        )}
        {conflictPolicy === 'OVERWRITE' && (
          <Alert
            showIcon
            type="warning"
            title="S3 覆盖不是原子操作"
            description="写入失败时目标目录可能处于不完整状态。"
          />
        )}
      </Form>
    </Space>
  );
};

export const CanvasNodeInspector = forwardRef<CanvasNodeInspectorHandle, CanvasNodeInspectorProps>(({
  node,
  validation,
  validationUnavailableMessage = null,
  executionMode = 'BATCH',
  onApply,
  onDirtyChange,
}, ref) => {
  if (!node) {
    return <Card size="small" className="canvas-inspector-empty">选中一个节点后配置其数据源、表和字段规则。</Card>;
  }
  const key = `${node.id}:${configurationFingerprint(node.configuration)}`;
  switch (node.type) {
    case CanvasNodeType.ModelInput:
      return (
        <ModelInputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.ModelOutput:
      return (
        <ModelOutputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.JdbcInput:
      return (
        <JdbcInputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.FileDatasetInput:
      return (
        <FileDatasetInputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.HttpApiInput:
      return (
        <HttpApiInputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.KafkaInput:
      return (
        <KafkaInputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.Join:
      return (
        <JoinInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.StreamJoin:
      return (
        <StreamJoinInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.Rename:
      return (
        <RenameInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.JdbcOutput:
      return (
        <JdbcOutputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          executionMode={executionMode}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.KafkaOutput:
      return (
        <KafkaOutputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
    case CanvasNodeType.FileOutput:
      return (
        <FileOutputInspector
          key={key}
          inspectorRef={ref}
          node={node}
          validation={validation}
          validationUnavailableMessage={validationUnavailableMessage}
          onApply={onApply}
          onDirtyChange={onDirtyChange}
        />
      );
  }
});

CanvasNodeInspector.displayName = 'CanvasNodeInspector';
