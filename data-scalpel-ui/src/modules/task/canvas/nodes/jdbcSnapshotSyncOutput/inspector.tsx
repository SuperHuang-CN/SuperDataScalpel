import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Space } from 'antd';
import { useImperativeHandle } from 'react';
import {
  useDataSource,
  useTableMetadata,
  type TableMetadata,
} from '../../../../datasource';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type JdbcSnapshotSyncOutputConfiguration,
} from '../../canvasTypes';
import { CanvasJdbcDataSourceSelect, CanvasJdbcTableSelect } from '../../components/CanvasJdbcSelectors';
import { ValidationIssues } from '../../components/CanvasLegacyInspectors';
import { SnapshotSyncConfigurationFields } from '../../components/SnapshotSyncConfigurationFields';
import {
  normalizeSnapshotSyncConfiguration,
  type SnapshotSyncFormValues,
} from '../../components/snapshotSyncConfiguration';
import { configurationFingerprint, focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import type { CanvasNodeInspectorComponentProps } from '../nodeSpec';

interface JdbcSnapshotSyncFormValues extends SnapshotSyncFormValues {
  dataSourceId: string;
  targetTableName: string;
}

const canvasColumns = (metadata: TableMetadata | undefined): CanvasColumnSchema[] => (
  metadata?.columns.flatMap((column): CanvasColumnSchema[] => {
    const type = column.platformTypeDefinition;
    return type ? [{
      name: column.name,
      fieldType: type.type,
      length: type.length,
      precision: type.precision,
      scale: type.scale,
      nullable: column.nullable,
      defaultValue: column.defaultValue,
      autoIncrement: column.autoIncrement,
      generated: column.generated,
      comment: column.comment,
      geometry: type.geometry ?? null,
    }] : [];
  }) ?? []
);

const JdbcSnapshotSyncOutputInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.JdbcSnapshotSyncOutput>) => {
  const [form] = Form.useForm<JdbcSnapshotSyncFormValues>();
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const targetTableName = Form.useWatch('targetTableName', form) ?? '';
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const tableQuery = useTableMetadata(
    dataSourceId || undefined,
    targetTableName ? { catalog: null, schema: null, table: targetTableName } : undefined,
    Boolean(dataSourceId && targetTableName),
  );
  const targetColumns = canvasColumns(tableQuery.data);
  const dataSourceAvailable = dataSourceQuery.data
    ? dataSourceQuery.data.enabled
      && dataSourceQuery.data.connectionKind === 'JDBC'
      && (dataSourceQuery.data.type === 'POSTGRESQL' || dataSourceQuery.data.type === 'HIGHGO'
        || dataSourceQuery.data.type === 'MYSQL' || dataSourceQuery.data.type === 'OPENGAUSS'
        || dataSourceQuery.data.type === 'KINGBASE')
      && dataSourceQuery.data.purposes.includes('DISTRIBUTION')
    : dataSourceQuery.isError ? false : undefined;
  const targetTableAvailable = tableQuery.data
    ? tableQuery.data.table.type.toUpperCase() === 'TABLE'
    : tableQuery.isError ? false : undefined;

  const toConfiguration = (values: JdbcSnapshotSyncFormValues): JdbcSnapshotSyncOutputConfiguration => ({
    ...normalizeSnapshotSyncConfiguration(values, targetColumns),
    dataSourceId: values.dataSourceId ?? '',
    targetTableName: values.targetTableName ?? '',
  });
  const markDirty = () => {
    const dirty = configurationFingerprint(toConfiguration(form.getFieldsValue(true)))
      !== configurationFingerprint(node.configuration);
    onDirtyChange(dirty);
  };
  const submit = (values: JdbcSnapshotSyncFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && dataSourceAvailable !== true) {
          form.setFields([{
            name: 'dataSourceId',
            errors: [dataSourceQuery.isFetching
              ? '正在读取数据源信息，请稍候'
              : '仅支持启用、具有数据分发用途的 PostgreSQL/HighGo/MySQL/openGauss/人大金仓数据源'],
          }]);

        }
        if (values.targetTableName && targetTableAvailable !== true) {
          form.setFields([{
            name: 'targetTableName',
            errors: [tableQuery.isFetching
              ? '正在读取目标表元数据，请稍候'
              : '目标必须是当前数据源中的普通物理表'],
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
      {dataSourceId && dataSourceQuery.isError && <Alert showIcon type="error" title="读取目标数据源失败" />}
      {targetTableName && tableQuery.isError && <Alert showIcon type="error" title="读取目标表元数据失败" />}
      <Form<JdbcSnapshotSyncFormValues>
        autoComplete="off"
        form={form}
        layout="vertical"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={markDirty}
      >
        <SnapshotSyncConfigurationFields
          inputTables={validation?.inputTables ?? []}
          validationReady={Boolean(validation)}
          targetColumns={targetColumns}
          uniqueKeys={tableQuery.data?.uniqueKeys ?? []}
          targetMetadataLoading={tableQuery.isFetching}
          initialConfiguration={node.configuration}
          onProgrammaticChange={() => queueMicrotask(markDirty)}
          targetSelector={(
            <>
              <Form.Item name="dataSourceId" label="目标数据源" rules={[{ required: true, message: '请选择目标数据源' }]}>
                <CanvasJdbcDataSourceSelect
                  purpose="DISTRIBUTION"
                  allowedTypes={['POSTGRESQL', 'HIGHGO', 'MYSQL', 'OPENGAUSS', 'KINGBASE']}
                  placeholder="选择支持快照同步的 JDBC 数据源"
                />
              </Form.Item>
              <Form.Item
                name="targetTableName"
                label="目标物理表"
                dependencies={['dataSourceId']}
                rules={[{ required: true, message: '请选择目标物理表' }]}
              >
                <CanvasJdbcTableSelect
                  dataSourceId={dataSourceId}
                  placeholder="输入表名搜索目标表"
                  selectedTableAvailable={targetTableAvailable}
                />
              </Form.Item>
            </>
          )}
        />
      </Form>
    </Space>
  );
};

export default JdbcSnapshotSyncOutputInspector;
