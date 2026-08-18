import { Alert, Form, Input, InputNumber, Select, Space } from 'antd';
import type { Ref } from 'react';
import { useImperativeHandle, useMemo } from 'react';
import { useDataSource, useDataSourceTypes, useTableMetadata } from '../../../../datasource';
import { CanvasJdbcDataSourceSelect, CanvasJdbcTableSelect } from '../../components/CanvasJdbcSelectors';
import { configurationFingerprint, focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import {
  CanvasNodeType,
  type CanvasNodeByType,
  type CanvasNodeConfigurationUpdateByType,
  type CanvasNodeValidationResult,
  type JdbcIncrementalInputConfiguration,
} from '../../canvasTypes';
import type { CanvasNodeInspectorHandle } from '../nodeSpec';

interface Props {
  node: CanvasNodeByType<typeof CanvasNodeType.JdbcIncrementalInput>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdateByType<typeof CanvasNodeType.JdbcIncrementalInput>) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

type FormValues = JdbcIncrementalInputConfiguration;

const quickIntervals = [10, 30, 60, 300].map((value) => ({
  value,
  label: value === 300 ? '5 分钟' : `${value} 秒`,
}));

const normalized = (values: Partial<FormValues>): JdbcIncrementalInputConfiguration => ({
  dataSourceId: values.dataSourceId ?? '',
  tableName: values.tableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  incrementalTimeColumn: values.incrementalTimeColumn ?? '',
  startPosition: values.startPosition ?? 'LATEST',
  startTime: values.startPosition === 'AT_TIME' && values.startTime ? values.startTime : null,
  cursorTimeZone: values.cursorTimeZone?.trim() || 'UTC',
  visibilityDelaySeconds: values.visibilityDelaySeconds ?? 30,
  triggerIntervalSeconds: values.triggerIntervalSeconds ?? 60,
});

export const JdbcIncrementalInputInspector = ({
  node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: Props) => {
  const [form] = Form.useForm<FormValues>();
  const dataSourceId = Form.useWatch('dataSourceId', form) ?? '';
  const tableName = Form.useWatch('tableName', form) ?? '';
  const startPosition = Form.useWatch('startPosition', form) ?? 'LATEST';
  const dataSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const typesQuery = useDataSourceTypes();
  const metadataQuery = useTableMetadata(
    dataSourceId || undefined,
    tableName ? { catalog: null, schema: null, table: tableName } : undefined,
    Boolean(dataSourceId && tableName),
  );
  const incrementalTypeIds = useMemo(() => new Set(
    (typesQuery.data ?? [])
      .filter((type) => type.capabilities.includes('JDBC_INCREMENTAL_READ'))
      .map((type) => type.id),
  ), [typesQuery.data]);
  const selectedSourceSupported = dataSourceQuery.data
    ? incrementalTypeIds.has(dataSourceQuery.data.type)
    : undefined;
  const timeColumns = (metadataQuery.data?.columns ?? []).filter((column) => (
    !column.nullable
    && (column.platformTypeDefinition?.type === 'TIMESTAMP'
      || column.platformTypeDefinition?.type === 'TIMESTAMP_NTZ')
  ));

  const submit = (values: FormValues) => {
    onApply({ id: node.id, type: node.type, configuration: normalized(values) });
    onDirtyChange(false);
  };
  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      try {
        const values = form.getFieldsValue(true);
        void form.validateFields().catch(() => undefined);
        if (values.dataSourceId && selectedSourceSupported === false) {
          form.setFields([{ name: 'dataSourceId', errors: ['数据源不支持 JDBC 增量读取'] }]);

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
    {(validationUnavailableMessage || validation?.issues.length) && <Alert
      type="warning" showIcon
      message={validationUnavailableMessage ?? validation?.issues[0]?.message}
    />}
    <Form<FormValues> form={form} layout="vertical" autoComplete="off"
      initialValues={node.configuration} onFinish={submit}
      onValuesChange={(_, values) => onDirtyChange(
        configurationFingerprint(normalized(values)) !== configurationFingerprint(node.configuration),
      )}>
      <Form.Item name="dataSourceId" label="来源数据源" rules={[{ required: true }]}>
        <CanvasJdbcDataSourceSelect
          purpose="SOURCE" placeholder="选择支持增量读取的 JDBC 数据源"
          allowedTypes={[...incrementalTypeIds]}
        />
      </Form.Item>
      <Form.Item name="tableName" label="物理表" rules={[{ required: true }]}>
        <CanvasJdbcTableSelect dataSourceId={dataSourceId} placeholder="选择普通物理表"
          selectedTableAvailable={metadataQuery.isError ? false : undefined} />
      </Form.Item>
      <Form.Item name="incrementalTimeColumn" label="增量时间字段" rules={[{ required: true }]}>
        <Select disabled={!tableName} loading={metadataQuery.isFetching}
          placeholder="选择非空 TIMESTAMP 字段"
          options={timeColumns.map((column) => ({
            value: column.name,
            label: `${column.name} · ${column.platformTypeDefinition?.type}`,
          }))} />
      </Form.Item>
      <Form.Item name="outputTableName" label="输出逻辑表名" rules={[
        { required: true, whitespace: true },
        { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' },
      ]}><Input placeholder="例如 order_changes" /></Form.Item>
      <Form.Item name="startPosition" label="首次启动位置" rules={[{ required: true }]}>
        <Select options={[
          { value: 'LATEST', label: 'LATEST · 从启动后的数据开始' },
          { value: 'EARLIEST', label: 'EARLIEST · 读取全部历史数据' },
          { value: 'AT_TIME', label: 'AT_TIME · 从指定时间之后开始' },
        ]} />
      </Form.Item>
      {startPosition === 'AT_TIME' && <Form.Item name="startTime" label="起始时间（RFC 3339）"
        rules={[{ required: true }]}><Input placeholder="2026-08-12T00:00:00Z" /></Form.Item>}
      <Form.Item name="cursorTimeZone" label="TIMESTAMP_NTZ 时区" rules={[{ required: true }]}>
        <Input placeholder="UTC" />
      </Form.Item>
      <Form.Item name="triggerIntervalSeconds" label="轮询间隔" rules={[{ required: true }]}>
        <Select options={quickIntervals} mode={undefined} />
      </Form.Item>
      <Form.Item name="visibilityDelaySeconds" label="可见性延迟（秒）" rules={[{ required: true }]}>
        <InputNumber min={0} max={3600} precision={0} style={{ width: '100%' }} />
      </Form.Item>
      <Alert type="warning" showIcon message="这是时间字段轮询，不是 CDC"
        description="不捕获物理删除；更新记录必须同步更新时间字段；失败恢复可能重放最后一个微批。建议为时间字段建立索引，并使用 UPSERT 等幂等输出。" />
    </Form>
  </Space>;
};

export default JdbcIncrementalInputInspector;
