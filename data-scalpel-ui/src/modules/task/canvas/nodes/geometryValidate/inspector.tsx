import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, Select, Space, Switch, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometryValidateConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import {
  spatialColumnOptions,
  spatialGeometryColumns,
  spatialTableOptions,
} from '../spatialInspectorOptions';

interface GeometryValidateFormValues {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  validColumnName: string;
  includeReason: boolean;
  reasonColumnName: string;
}

const toConfiguration = (
  values: GeometryValidateFormValues,
): GeometryValidateConfiguration => ({
  sourceTableName: values.sourceTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  geometryColumnName: values.geometryColumnName ?? '',
  validColumnName: values.validColumnName?.trim() ?? '',
  reasonColumnName: values.includeReason ? values.reasonColumnName?.trim() ?? '' : null,
});

const fingerprint = (value: GeometryValidateConfiguration) => JSON.stringify(value);

const GeometryValidateInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometryValidate>) => {
  const [form] = Form.useForm<GeometryValidateFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const includeReason = Form.useWatch('includeReason', form) ?? false;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const geometryMissing = Boolean(
    geometryColumnName
    && sourceTable
    && !geometryColumns.some((column) => column.name === geometryColumnName),
  );
  const initialValues: GeometryValidateFormValues = {
    sourceTableName: node.configuration.sourceTableName,
    outputTableName: node.configuration.outputTableName,
    geometryColumnName: node.configuration.geometryColumnName,
    validColumnName: node.configuration.validColumnName,
    includeReason: node.configuration.reasonColumnName !== null,
    reasonColumnName: node.configuration.reasonColumnName ?? 'geometry_invalid_reason',
  };

  const submit = (values: GeometryValidateFormValues) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        try {
          void form.validateFields().catch(() => undefined);
          submit(form.getFieldsValue(true));
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
        title="只诊断，不修复"
        description="节点保留所有行和原 Geometry。无效 Geometry 只写入诊断结果，不会被删除，也不会让任务失败。"
      />
      <Form<GeometryValidateFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={initialValues}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(fingerprint(toConfiguration(values)) !== fingerprint(node.configuration));
        }}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，诊断配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)}
            placeholder={validation ? '选择来源表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item
          name="geometryColumnName"
          label="Geometry 字段"
          rules={[{ required: true, message: '请选择 Geometry 字段' }]}
          validateStatus={geometryMissing ? 'error' : undefined}
          help={geometryMissing ? '原 Geometry 字段已失效，配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!sourceTable}
            options={spatialColumnOptions(
              sourceTable?.columns ?? [],
              geometryColumnName,
              (column) => column.fieldType === 'GEOMETRY',
            )}
            placeholder="选择 Geometry 字段"
          />
        </Form.Item>
        <Form.Item
          name="validColumnName"
          label="合法性结果字段"
          rules={[{ required: true, whitespace: true, message: '请输入结果字段名' }]}
        >
          <Input placeholder="geometry_valid" />
        </Form.Item>
        <Form.Item name="includeReason" label="输出无效原因" valuePropName="checked">
          <Switch checkedChildren="输出" unCheckedChildren="不输出" />
        </Form.Item>
        {includeReason && (
          <Form.Item
            name="reasonColumnName"
            label="无效原因字段"
            rules={[{ required: true, whitespace: true, message: '请输入原因字段名' }]}
          >
            <Input placeholder="geometry_invalid_reason" />
          </Form.Item>
        )}
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 parcels_validated" />
        </Form.Item>
        <Typography.Text type="secondary">
          NULL Geometry 的合法性和原因均输出 NULL；有效 Geometry 的原因也输出 NULL。
        </Typography.Text>
      </Form>
    </Space>
  );
};

export default GeometryValidateInspector;
