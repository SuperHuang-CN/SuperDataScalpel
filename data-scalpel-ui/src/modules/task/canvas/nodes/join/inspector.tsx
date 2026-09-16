import { DeleteOutlined,PlusOutlined } from '@ant-design/icons';
import { Button,Card,Form,Input,Select,Space,Typography } from 'antd';
import { useEffect,useImperativeHandle,type Ref } from 'react';
import { platformTypeLabel } from '../../canvasSchema';
import {
CanvasNodeType,type CanvasExecutionMode,type CanvasNodeConfigurationUpdate,
type CanvasNodeDefinition,type CanvasNodeValidationResult,type JoinCondition,
type JoinConfiguration,
type JoinOutputColumn
} from '../../canvasTypes';
import { configurationFingerprint,focusFirstInvalidField } from '../../components/CanvasInspectorUtils';
import { JoinOutputColumnsEditor } from '../../components/JoinOutputColumnsEditor';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { suggestJoinOutputColumns } from '../../components/joinOutputColumns';
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
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
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
const JoinCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Join, JoinInspector);
export default JoinCanvasNodeInspector;

