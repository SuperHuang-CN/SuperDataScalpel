import { Checkbox,Form,Input,InputNumber,Select,Space,Tag,Typography } from 'antd';
import { useImperativeHandle,type Ref } from 'react';
import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { useDataSource, type DataSource } from '../../../../datasource';
import {
CanvasNodeType,type CanvasExecutionMode,type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,type CanvasNodeValidationResult,type KafkaInputConfiguration,
type KafkaInputMetadataField,
type KafkaInputValueFormat,type KafkaValueSchema
} from '../../canvasTypes';
import { CanvasInspectorFieldLabel } from '../../components/CanvasInspectorFieldLabel';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { CanvasKafkaDataSourceSelect,CanvasKafkaTopicSelect } from '../../components/CanvasKafkaSelectors';
import { KafkaValueSchemaEditor } from '../../components/KafkaValueSchemaEditor';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

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

const kafkaDataSourceAvailable = (
  dataSource: DataSource | undefined,
  purpose: 'SOURCE' | 'DISTRIBUTION',
) => (
  dataSource !== undefined
  && dataSource.enabled
  && dataSource.connectionKind === 'KAFKA'
  && dataSource.purposes.includes(purpose)
);


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
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
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
const KafkaInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.KafkaInput, KafkaInputInspector);
export default KafkaInputCanvasNodeInspector;
