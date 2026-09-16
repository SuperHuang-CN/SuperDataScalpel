import { SearchOutlined } from '@ant-design/icons';
import { Form,Input,Select,Space,Table,Tag,Typography } from 'antd';
import { useImperativeHandle,useState,type Ref } from 'react';
import { useDataSource, type DataSource } from '../../../../datasource';
import { platformTypeLabel } from '../../canvasSchema';
import {
type CanvasColumnSchema,
type CanvasExecutionMode,
type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,
type CanvasNodeValidationResult,
type JdbcColumnMapping,
type KafkaOutputConfiguration,
type KafkaOutputValueFormat,
type KafkaValueSchema
} from '../../canvasTypes';
import { CanvasInspectorFieldLabel } from '../../components/CanvasInspectorFieldLabel';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { CanvasKafkaDataSourceSelect,CanvasKafkaTopicSelect } from '../../components/CanvasKafkaSelectors';
import { KafkaValueSchemaEditor } from '../../components/KafkaValueSchemaEditor';
import {
OutputFieldMappingFields,
} from '../../components/OutputFieldMappingFields';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { orderOutputFieldMappings } from '../../components/outputFieldMappings';

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

const kafkaDataSourceAvailable = (
  dataSource: DataSource | undefined,
  purpose: 'SOURCE' | 'DISTRIBUTION',
) => (
  dataSource !== undefined
  && dataSource.enabled
  && dataSource.connectionKind === 'KAFKA'
  && dataSource.purposes.includes(purpose)
);


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
      {!hideNodeValidation && <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
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
