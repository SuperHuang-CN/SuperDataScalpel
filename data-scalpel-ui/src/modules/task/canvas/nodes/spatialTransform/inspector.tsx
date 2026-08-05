import { Alert, Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type SpatialTransformConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';

interface SpatialTransformFormValues {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  targetEpsgCode: number | null;
}

const fingerprint = (value: SpatialTransformConfiguration) => JSON.stringify(value);

const toConfiguration = (
  values: SpatialTransformFormValues,
): SpatialTransformConfiguration => ({
  sourceTableName: values.sourceTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  geometryColumnName: values.geometryColumnName ?? '',
  targetCrs: Number.isInteger(values.targetEpsgCode) && (values.targetEpsgCode ?? 0) > 0
    ? { authority: 'EPSG', code: values.targetEpsgCode as number }
    : null,
});

const SpatialTransformInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialTransform>) => {
  const [form] = Form.useForm<SpatialTransformFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = sourceTable?.columns.filter(
    (column) => column.fieldType === 'GEOMETRY',
  ) ?? [];
  const initialValues: SpatialTransformFormValues = {
    sourceTableName: node.configuration.sourceTableName,
    outputTableName: node.configuration.outputTableName,
    geometryColumnName: node.configuration.geometryColumnName,
    targetEpsgCode: node.configuration.targetCrs?.code ?? null,
  };

  const submit = (values: SpatialTransformFormValues) => {
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
        title="显式坐标转换"
        description="来源 CRS 由 Task Engine 从上游 Geometry Schema 推导；第一阶段只支持 EPSG 和 XY。"
      />
      <Form<SpatialTransformFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={initialValues}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(
            fingerprint(toConfiguration(values))
              !== fingerprint(node.configuration),
          );
        }}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
        >
          <Select
            disabled={!validation}
            placeholder={validation ? '选择来源表' : '等待 Task Engine 计算上游表'}
            options={tables.map((table) => ({ value: table.name, label: table.name }))}
            onChange={() => form.setFieldValue('geometryColumnName', '')}
          />
        </Form.Item>
        <Form.Item
          name="geometryColumnName"
          label="Geometry 字段"
          rules={[{ required: true, message: '请选择 Geometry 字段' }]}
          extra={sourceTable && geometryColumns.length === 0
            ? '所选上游表没有可执行的 Geometry 字段'
            : undefined}
        >
          <Select
            disabled={!validation || !sourceTable}
            placeholder="选择 Geometry 字段"
            options={geometryColumns.map((column) => ({
              value: column.name,
              label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="targetEpsgCode"
          label="目标 CRS"
          rules={[
            { required: true, message: '请输入目标 EPSG code' },
            {
              validator: (_, value: number | null) => (
                Number.isInteger(value) && (value ?? 0) > 0
                  ? Promise.resolve()
                  : Promise.reject(new Error('EPSG code 必须是正整数'))
              ),
            },
          ]}
        >
          <InputNumber
            min={1}
            precision={0}
            className="canvas-full-width"
            addonBefore="EPSG"
            placeholder="例如 3857"
          />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' },
          ]}
        >
          <Input placeholder="例如 orders_3857" />
        </Form.Item>
        {sourceTable && (
          <Typography.Text type="secondary">
            上游 Schema 由 Task Engine 返回，共 {sourceTable.columns.length} 个字段，
            其中 {geometryColumns.length} 个 Geometry 字段。
          </Typography.Text>
        )}
      </Form>
    </Space>
  );
};

export default SpatialTransformInspector;
