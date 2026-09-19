import { createUuid } from '../../../../../shared/browser/createUuid';
import { Button,Form,Select,Space } from 'antd';
import { useImperativeHandle,type Ref } from 'react';
import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { ApiError } from '../../../../../shared/api/http';
import {
  useDataSource,
  useTableMetadata,
  type DataSource,
  type TableMetadata,
} from '../../../../datasource';
import {
type CanvasColumnSchema,
type CanvasExecutionMode,
type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,
type CanvasNodeValidationResult,
type JdbcColumnMapping,
type JdbcOutputConfiguration
} from '../../canvasTypes';
import { CanvasInspectorFieldLabel } from '../../components/CanvasInspectorFieldLabel';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { CanvasJdbcDataSourceSelect,CanvasJdbcTableSelect } from '../../components/CanvasJdbcSelectors';
import {
OutputFieldMappingFields,
} from '../../components/OutputFieldMappingFields';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { orderOutputFieldMappings } from '../../components/outputFieldMappings';
import { jdbcWriteModeUnavailableReason } from '../../jdbcDatabaseCapabilities';

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
        writeId: node.configuration.writes?.[0]?.writeId ?? createUuid(),
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
      {!hideNodeValidation && <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
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
