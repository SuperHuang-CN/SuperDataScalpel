import { EyeOutlined } from '@ant-design/icons';
import { Button,Form,Select,Space,Tag,Tooltip,Typography } from 'antd';
import { useImperativeHandle,useState,type Ref } from 'react';
import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { useDataSource } from '../../../../datasource';
import {
dataModelStatusLabels,
useDataModel,
type DataModelDetail,
type DataModelField,
} from '../../../../model';
import {
type CanvasColumnSchema,
type CanvasExecutionMode,
type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,
type CanvasNodeValidationResult,
type JdbcColumnMapping,
type ModelOutputConfiguration
} from '../../canvasTypes';
import { CanvasInspectorFieldLabel } from '../../components/CanvasInspectorFieldLabel';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { CanvasModelDetailModal } from '../../components/CanvasModelDetailModal';
import { CanvasModelSelect } from '../../components/CanvasModelSelect';
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
      {!hideNodeValidation && <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />}
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
