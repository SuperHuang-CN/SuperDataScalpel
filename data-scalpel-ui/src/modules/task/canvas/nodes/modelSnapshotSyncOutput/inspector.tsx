import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { EyeOutlined } from '@ant-design/icons';
import { Button, Form, Space, Tooltip } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  dataModelStatusLabels,
  type DataModelField,
  useDataModel,
} from '../../../../model';
import { useDataSource } from '../../../../datasource';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type ModelSnapshotSyncOutputConfiguration,
} from '../../canvasTypes';
import { CanvasModelSelect } from '../../components/CanvasModelSelect';
import { CanvasModelDetailModal } from '../../components/CanvasModelDetailModal';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { SnapshotSyncConfigurationFields } from '../../components/SnapshotSyncConfigurationFields';
import {
  normalizeSnapshotSyncConfiguration,
  type SnapshotSyncFormValues,
} from '../../components/snapshotSyncConfiguration';
import { configurationFingerprint, focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import type { CanvasNodeInspectorComponentProps } from '../nodeSpec';

interface ModelSnapshotSyncFormValues extends SnapshotSyncFormValues {
  targetModelId: string;
}

const SNAPSHOT_SYNC_MODEL_MODES = ['MANAGED'] as const;

const canvasColumns = (
  fields: readonly DataModelField[] | undefined,
): CanvasColumnSchema[] => (
  [...(fields ?? [])]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((field) => ({
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

const ModelSnapshotSyncOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.ModelSnapshotSyncOutput>) => {
  const [form] = Form.useForm<ModelSnapshotSyncFormValues>();
  const [modelDetailOpen, setModelDetailOpen] = useState(false);
  const targetModelId = Form.useWatch('targetModelId', form) ?? '';
  const modelQuery = useDataModel(targetModelId || undefined, Boolean(targetModelId));
  const model = modelQuery.data?.model;
  const dataSourceQuery = useDataSource(
    model?.storageDataSourceId,
    Boolean(model?.storageDataSourceId),
  );
  const targetColumns = canvasColumns(modelQuery.data?.fields);
  const primaryKeyColumns = [...(modelQuery.data?.fields ?? [])]
    .filter((field) => field.primaryKey)
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((field) => field.code);
  const logicalUniqueKeys = primaryKeyColumns.length > 0 ? [{
    name: 'MODEL_PRIMARY_KEY',
    type: 'PRIMARY_KEY' as const,
    columns: primaryKeyColumns,
  }] : [];
  const dataSourceAvailable = dataSourceQuery.data
    ? dataSourceQuery.data.enabled
      && dataSourceQuery.data.connectionKind === 'JDBC'
      && (dataSourceQuery.data.type === 'POSTGRESQL' || dataSourceQuery.data.type === 'HIGHGO'
        || dataSourceQuery.data.type === 'MYSQL' || dataSourceQuery.data.type === 'OPENGAUSS'
        || dataSourceQuery.data.type === 'KINGBASE')
      && dataSourceQuery.data.purposes.includes('STORAGE')
    : dataSourceQuery.isError ? false : undefined;
  const modelUnavailable = modelQuery.isError
    ? '模型不存在或模型详情读取失败'
    : model && model.status !== 'PUBLISHED'
      ? `模型当前状态为${dataModelStatusLabels[model.status]}，只有已发布模型可用`
      : model && model.physicalTableMode !== 'MANAGED'
        ? '模型快照同步首版只支持 MANAGED 模型'
        : dataSourceAvailable === false
          ? '模型存储必须使用启用、具有 STORAGE 用途的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓数据源'
          : null;

  const toConfiguration = (values: ModelSnapshotSyncFormValues): ModelSnapshotSyncOutputConfiguration => ({
    ...normalizeSnapshotSyncConfiguration(values, targetColumns),
    targetModelId: values.targetModelId ?? '',
  });
  const markDirty = () => {
    const dirty = configurationFingerprint(toConfiguration(form.getFieldsValue(true)))
      !== configurationFingerprint(node.configuration);
    onDirtyChange(dirty);
  };
  const submit = (values: ModelSnapshotSyncFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (modelQuery.isFetching && !modelQuery.data
          || dataSourceQuery.isFetching && !dataSourceQuery.data) {
          form.setFields([{ name: 'targetModelId', errors: ['正在读取模型目标信息，请稍候'] }]);

        }
        if (modelUnavailable) {
          form.setFields([{ name: 'targetModelId', errors: [modelUnavailable] }]);

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
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      {modelUnavailable && <Alert showIcon type="error" title={modelUnavailable} />}
      <Form<ModelSnapshotSyncFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(changed) => {
          if (changed.targetModelId !== undefined) setModelDetailOpen(false);
          markDirty();
        }}
      >
        <SnapshotSyncConfigurationFields
          inputTables={validation?.inputTables ?? []}
          validationReady={Boolean(validation)}
          targetColumns={targetColumns}
          targetFieldNames={new Map(
            (modelQuery.data?.fields ?? []).map((field) => [field.code, field.name]),
          )}
          uniqueKeys={logicalUniqueKeys}
          targetMetadataLoading={modelQuery.isFetching}
          initialConfiguration={node.configuration}
          onProgrammaticChange={() => queueMicrotask(markDirty)}
          targetSelector={(
            <Form.Item label="目标模型" required>
              <div className="canvas-resource-select-with-action">
                <Form.Item name="targetModelId" noStyle rules={[{ required: true, message: '请选择目标模型' }]}>
                  <CanvasModelSelect
                    placeholder="选择已发布 MANAGED 模型"
                    physicalTableModes={SNAPSHOT_SYNC_MODEL_MODES}
                  />
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
          )}
        />
      </Form>
      <CanvasModelDetailModal
        detail={modelQuery.data}
        open={modelDetailOpen}
        onClose={() => setModelDetailOpen(false)}
      />
    </Space>
  );
};

export default ModelSnapshotSyncOutputInspector;
