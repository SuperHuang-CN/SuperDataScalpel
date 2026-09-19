import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, Select, Space, Switch, Tag, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometryExplodeConfiguration,
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

interface GeometryExplodeFormValues {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  includePartIndex: boolean;
  partIndexColumnName: string;
}

const componentKind = (kind: string | undefined) => {
  if (kind === 'MULTIPOINT') return 'POINT';
  if (kind === 'MULTILINESTRING') return 'LINESTRING';
  if (kind === 'MULTIPOLYGON') return 'POLYGON';
  if (kind === 'POINT' || kind === 'LINESTRING' || kind === 'POLYGON') return kind;
  return 'GEOMETRY';
};

const toConfiguration = (
  values: GeometryExplodeFormValues,
): GeometryExplodeConfiguration => ({
  sourceTableName: values.sourceTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  geometryColumnName: values.geometryColumnName ?? '',
  outputColumnName: values.outputColumnName?.trim() ?? '',
  partIndexColumnName: values.includePartIndex
    ? values.partIndexColumnName?.trim() ?? '' : null,
});

const fingerprint = (value: GeometryExplodeConfiguration) => JSON.stringify(value);

const GeometryExplodeInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometryExplode>) => {
  const [form] = Form.useForm<GeometryExplodeFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const includePartIndex = Form.useWatch('includePartIndex', form) ?? false;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const geometryColumn = geometryColumns.find((column) => column.name === geometryColumnName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const geometryMissing = Boolean(
    geometryColumnName
    && sourceTable
    && !geometryColumn,
  );
  const initialValues: GeometryExplodeFormValues = {
    sourceTableName: node.configuration.sourceTableName,
    outputTableName: node.configuration.outputTableName,
    geometryColumnName: node.configuration.geometryColumnName,
    outputColumnName: node.configuration.outputColumnName,
    includePartIndex: node.configuration.partIndexColumnName !== null,
    partIndexColumnName: node.configuration.partIndexColumnName ?? 'part_index',
  };

  const submit = (values: GeometryExplodeFormValues) => {
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
        type="warning"
        title="该节点可能增加行数"
        description="每个 Geometry 部件输出一行并复制原属性。NULL 或 Empty 输入保留一行，部件输出 NULL。"
      />
      <Form<GeometryExplodeFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，拆分配置仍被保留。' : undefined}
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
            placeholder="选择 MultiGeometry 或集合字段"
          />
        </Form.Item>
        {geometryColumn && (
          <Typography.Text type="secondary">
            预计部件类型：<Tag>{componentKind(geometryColumn.geometry?.kind)}</Tag>
          </Typography.Text>
        )}
        <Form.Item
          name="outputColumnName"
          label="部件 Geometry 字段"
          rules={[{ required: true, whitespace: true, message: '请输入部件字段名' }]}
        >
          <Input placeholder="geometry_part" />
        </Form.Item>
        <Form.Item name="includePartIndex" label="输出部件序号" valuePropName="checked">
          <Switch checkedChildren="输出" unCheckedChildren="不输出" />
        </Form.Item>
        {includePartIndex && (
          <Form.Item
            name="partIndexColumnName"
            label="部件序号字段"
            rules={[{ required: true, whitespace: true, message: '请输入序号字段名' }]}
            extra="序号从 0 开始；NULL 或 Empty 行的序号为 NULL。"
          >
            <Input placeholder="part_index" />
          </Form.Item>
        )}
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 parcel_parts" />
        </Form.Item>
      </Form>
    </Space>
  );
};

export default GeometryExplodeInspector;
