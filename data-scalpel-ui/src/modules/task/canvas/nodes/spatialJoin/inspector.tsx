import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type SpatialJoinCondition,
  type SpatialJoinConfiguration,
  type SpatialPredicate,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';

interface SpatialJoinFormValues {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: 'INNER';
  conditions: SpatialJoinCondition[];
}

const predicates: readonly { value: SpatialPredicate; label: string }[] = [
  { value: 'INTERSECTS', label: 'INTERSECTS · 相交' },
  { value: 'CONTAINS', label: 'CONTAINS · 左侧包含右侧' },
  { value: 'WITHIN', label: 'WITHIN · 左侧位于右侧内' },
  { value: 'COVERS', label: 'COVERS · 左侧覆盖右侧' },
  { value: 'COVERED_BY', label: 'COVERED_BY · 左侧被右侧覆盖' },
  { value: 'TOUCHES', label: 'TOUCHES · 边界接触' },
  { value: 'OVERLAPS', label: 'OVERLAPS · 重叠' },
  { value: 'CROSSES', label: 'CROSSES · 穿越' },
  { value: 'EQUALS', label: 'EQUALS · 空间相等' },
];

const fingerprint = (value: SpatialJoinConfiguration) => JSON.stringify(value);

const toConfiguration = (values: SpatialJoinFormValues): SpatialJoinConfiguration => ({
  leftTableName: values.leftTableName ?? '',
  rightTableName: values.rightTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  joinType: 'INNER',
  conditions: (values.conditions ?? []).map((condition) => ({
    leftGeometryColumnName: condition.leftGeometryColumnName ?? '',
    predicate: condition.predicate ?? null,
    rightGeometryColumnName: condition.rightGeometryColumnName ?? '',
  })),
});

const SpatialJoinInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialJoin>) => {
  const [form] = Form.useForm<SpatialJoinFormValues>();
  const leftTableName = Form.useWatch('leftTableName', form) ?? '';
  const rightTableName = Form.useWatch('rightTableName', form) ?? '';
  const conditions = Form.useWatch('conditions', form) ?? [];
  const tables = validation?.inputTables ?? [];
  const leftTable = tables.find((table) => table.name === leftTableName);
  const rightTable = tables.find((table) => table.name === rightTableName);
  const leftGeometryColumns = leftTable?.columns.filter(
    (column) => column.fieldType === 'GEOMETRY',
  ) ?? [];
  const rightGeometryColumns = rightTable?.columns.filter(
    (column) => column.fieldType === 'GEOMETRY',
  ) ?? [];

  const submit = (values: SpatialJoinFormValues) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: toConfiguration(values),
    });
    onDirtyChange(false);
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        try {
          submit(await form.validateFields());
          return true;
        } catch {
          return false;
        }
      },
    }),
  );

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Alert
        showIcon
        type="info"
        title="第一阶段仅支持 INNER 空间连接"
        description="两侧 Geometry 的 kind 可以不同，但 CRS 和 dimension 必须完全一致；不一致时请先加入空间转换节点。"
      />
      <Form<SpatialJoinFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            fingerprint(toConfiguration(values))
              !== fingerprint(node.configuration),
          );
        }}
      >
        <Form.Item
          name="leftTableName"
          label="左表"
          rules={[{ required: true, message: '请选择左表' }]}
        >
          <Select
            disabled={!validation}
            placeholder={validation ? '选择左表' : '等待 Task Engine 计算上游表'}
            options={tables.map((table) => ({ value: table.name, label: table.name }))}
            onChange={() => form.setFieldValue('conditions', [])}
          />
        </Form.Item>
        <Form.Item
          name="rightTableName"
          label="右表"
          rules={[{ required: true, message: '请选择右表' }]}
        >
          <Select
            disabled={!validation}
            placeholder={validation ? '选择右表' : '等待 Task Engine 计算上游表'}
            options={tables
              .filter((table) => table.name !== leftTableName)
              .map((table) => ({ value: table.name, label: table.name }))}
            onChange={() => form.setFieldValue('conditions', [])}
          />
        </Form.Item>
        <Form.Item name="joinType" label="连接类型">
          <Select disabled options={[{ value: 'INNER', label: 'INNER' }]} />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' },
          ]}
        >
          <Input placeholder="例如 orders_with_region" />
        </Form.Item>
        <Typography.Text strong>空间条件</Typography.Text>
        <Form.List
          name="conditions"
          rules={[{
            validator: (_, value: SpatialJoinCondition[] | undefined) => (
              value && value.length >= 1 && value.length <= 8
                ? Promise.resolve()
                : Promise.reject(new Error('空间条件必须为 1..8 项'))
            ),
          }]}
        >
          {(fields, { add, remove }, { errors }) => (
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
                      aria-label={`删除空间条件 ${index + 1}`}
                      onClick={() => remove(field.name)}
                    />
                  )}
                >
                  <Form.Item
                    name={[field.name, 'leftGeometryColumnName']}
                    rules={[{ required: true, message: '请选择左侧 Geometry 字段' }]}
                  >
                    <Select
                      disabled={!validation || !leftTable}
                      placeholder="左侧 Geometry 字段"
                      options={leftGeometryColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    name={[field.name, 'predicate']}
                    rules={[{ required: true, message: '请选择空间谓词' }]}
                  >
                    <Select placeholder="空间谓词" options={[...predicates]} />
                  </Form.Item>
                  <Form.Item
                    name={[field.name, 'rightGeometryColumnName']}
                    rules={[{ required: true, message: '请选择右侧 Geometry 字段' }]}
                  >
                    <Select
                      disabled={!validation || !rightTable}
                      placeholder="右侧 Geometry 字段"
                      options={rightGeometryColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
                      }))}
                    />
                  </Form.Item>
                </Card>
              ))}
              <Button
                icon={<PlusOutlined />}
                disabled={conditions.length >= 8}
                onClick={() => add({
                  leftGeometryColumnName: '',
                  predicate: 'INTERSECTS',
                  rightGeometryColumnName: '',
                })}
              >
                添加空间条件
              </Button>
              <Form.ErrorList errors={errors} />
            </Space>
          )}
        </Form.List>
      </Form>
    </Space>
  );
};

export default SpatialJoinInspector;
