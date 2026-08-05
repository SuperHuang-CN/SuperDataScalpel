import { Alert, Form, Input, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialClipConfiguration,
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

const fingerprint = (value: SpatialClipConfiguration) => JSON.stringify(value);

const normalize = (values: SpatialClipConfiguration): SpatialClipConfiguration => ({
  sourceTableName: values.sourceTableName ?? '',
  maskTableName: values.maskTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  sourceGeometryColumnName: values.sourceGeometryColumnName ?? '',
  maskGeometryColumnName: values.maskGeometryColumnName ?? '',
  outputColumnName: values.outputColumnName?.trim() ?? '',
});

const sameSpatialReference = (
  source: CanvasColumnSchema | undefined,
  mask: CanvasColumnSchema | undefined,
) => Boolean(
  source?.geometry
  && mask?.geometry
  && source.geometry.crs.authority === mask.geometry.crs.authority
  && source.geometry.crs.code === mask.geometry.crs.code
  && source.geometry.dimension === mask.geometry.dimension,
);

const geometryTag = (column: CanvasColumnSchema | undefined) => {
  if (!column?.geometry) return null;
  return (
    <Tag color="purple">
      {`${column.geometry.kind} · ${column.geometry.crs.authority}:${column.geometry.crs.code} · ${column.geometry.dimension}`}
    </Tag>
  );
};

const SpatialClipInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialClip>) => {
  const [form] = Form.useForm<SpatialClipConfiguration>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const maskTableName = Form.useWatch('maskTableName', form) ?? '';
  const sourceGeometryColumnName = Form.useWatch('sourceGeometryColumnName', form) ?? '';
  const maskGeometryColumnName = Form.useWatch('maskGeometryColumnName', form) ?? '';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const maskTable = tables.find((table) => table.name === maskTableName);
  const sourceGeometryColumns = spatialGeometryColumns(sourceTable);
  const maskGeometryColumns = spatialGeometryColumns(maskTable);
  const sourceGeometry = sourceGeometryColumns.find(
    (column) => column.name === sourceGeometryColumnName,
  );
  const maskGeometry = maskGeometryColumns.find(
    (column) => column.name === maskGeometryColumnName,
  );
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const maskTableMissing = Boolean(maskTableName && validation && !maskTable);
  const sourceGeometryMissing = Boolean(
    sourceGeometryColumnName && sourceTable && !sourceGeometry,
  );
  const maskGeometryMissing = Boolean(
    maskGeometryColumnName
    && maskTable
    && (!maskGeometry
      || maskGeometry.geometry?.kind !== 'POLYGON'
        && maskGeometry.geometry?.kind !== 'MULTIPOLYGON'),
  );
  const sameTable = Boolean(sourceTableName && sourceTableName === maskTableName);
  const referenceMismatch = Boolean(sourceGeometry && maskGeometry)
    && !sameSpatialReference(sourceGeometry, maskGeometry);

  const submit = (values: SpatialClipConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(values) });
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
        title="INNER 裁剪"
        description="仅保留非空相交结果；一个来源命中多个 Mask 时会输出多行，Mask 属性不会进入结果。"
      />
      <Form<SpatialClipConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(fingerprint(normalize(values)) !== fingerprint(node.configuration));
        }}
      >
        <Typography.Text strong>来源</Typography.Text>
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)}
            placeholder={validation ? '选择被裁剪的来源表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item
          name="sourceGeometryColumnName"
          label="来源 Geometry"
          rules={[{ required: true, message: '请选择来源 Geometry 字段' }]}
          validateStatus={sourceGeometryMissing ? 'error' : undefined}
          help={sourceGeometryMissing ? '原字段已失效，配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!sourceTable}
            options={spatialColumnOptions(
              sourceTable?.columns ?? [],
              sourceGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY',
            )}
            placeholder="选择任意 Geometry 字段"
          />
        </Form.Item>
        {geometryTag(sourceGeometry)}

        <Typography.Text strong>Mask</Typography.Text>
        <Form.Item
          name="maskTableName"
          label="Mask 表"
          rules={[{ required: true, message: '请选择 Mask 表' }]}
          validateStatus={maskTableMissing || sameTable ? 'error' : undefined}
          help={maskTableMissing
            ? '原 Mask 表已失效，配置仍被保留。'
            : sameTable ? '来源表与 Mask 表不能相同。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={spatialTableOptions(tables, maskTableName)}
            placeholder="选择面状 Mask 表"
          />
        </Form.Item>
        <Form.Item
          name="maskGeometryColumnName"
          label="Mask Geometry"
          rules={[{ required: true, message: '请选择 Mask Geometry 字段' }]}
          validateStatus={maskGeometryMissing || referenceMismatch ? 'error' : undefined}
          help={maskGeometryMissing
            ? 'Mask 只接受 Polygon/MultiPolygon；原失效值仍被保留。'
            : referenceMismatch
              ? '来源与 Mask 的 CRS 或 dimension 不一致，请先使用空间转换节点。'
              : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!maskTable}
            options={spatialColumnOptions(
              maskTable?.columns ?? [],
              maskGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY'
                && (column.geometry?.kind === 'POLYGON'
                  || column.geometry?.kind === 'MULTIPOLYGON'),
            )}
            placeholder="选择 Polygon 或 MultiPolygon"
          />
        </Form.Item>
        {geometryTag(maskGeometry)}

        <Typography.Text strong>输出</Typography.Text>
        <Form.Item
          name="outputColumnName"
          label="裁剪 Geometry 字段"
          rules={[{ required: true, whitespace: true, message: '请输入输出字段名' }]}
        >
          <Input placeholder="例如 clipped_geometry" />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 district_roads" />
        </Form.Item>
        <Typography.Text type="secondary">
          结果字段使用通用 GEOMETRY，以安全表达裁剪后的升维或降维结果。
        </Typography.Text>
      </Form>
    </Space>
  );
};

export default SpatialClipInspector;
