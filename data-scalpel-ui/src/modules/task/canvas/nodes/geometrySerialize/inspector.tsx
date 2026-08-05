import { Alert, Form, Input, Radio, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometrySerializeConfiguration,
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

const fingerprint = (value: GeometrySerializeConfiguration) => JSON.stringify(value);

const normalize = (
  values: GeometrySerializeConfiguration,
): GeometrySerializeConfiguration => ({
  sourceTableName: values.sourceTableName ?? '',
  outputTableName: values.outputTableName?.trim() ?? '',
  geometryColumnName: values.geometryColumnName ?? '',
  outputColumnName: values.outputColumnName?.trim() ?? '',
  format: values.format ?? 'WKT',
});

const GeometrySerializeInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometrySerialize>) => {
  const [form] = Form.useForm<GeometrySerializeConfiguration>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const format = Form.useWatch('format', form) ?? 'WKT';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const selectedGeometry = geometryColumns.find((column) => column.name === geometryColumnName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const geometryMissing = Boolean(
    geometryColumnName
    && sourceTable
    && !selectedGeometry,
  );
  const geoJsonCrsInvalid = format === 'GEOJSON'
    && selectedGeometry?.geometry
    && (selectedGeometry.geometry.crs.authority !== 'EPSG'
      || selectedGeometry.geometry.crs.code !== 4326);

  const submit = (values: GeometrySerializeConfiguration) => {
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
        title="保留原 Geometry"
        description="节点在字段末尾追加 WKT、WKB 或 GeoJSON。写入 Kafka 前可再用字段选择节点移除原 Geometry。"
      />
      <Form<GeometrySerializeConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={(_changed, values) => {
          onDirtyChange(fingerprint(normalize(values)) !== fingerprint(node.configuration));
        }}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，序列化配置仍被保留。' : undefined}
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
          validateStatus={geometryMissing || geoJsonCrsInvalid ? 'error' : undefined}
          help={geometryMissing
            ? '原 Geometry 字段已失效，配置仍被保留。'
            : geoJsonCrsInvalid
              ? 'GeoJSON 只支持 EPSG:4326，请先连接空间转换节点。'
              : undefined}
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
        <Form.Item name="format" label="序列化格式" rules={[{ required: true }]}>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 'WKT', label: 'WKT · STRING' },
              { value: 'WKB', label: 'WKB · BINARY' },
              { value: 'GEOJSON', label: 'GeoJSON · STRING' },
            ]}
          />
        </Form.Item>
        {format === 'GEOJSON' && (
          <Alert
            showIcon
            type={geoJsonCrsInvalid ? 'error' : 'warning'}
            title="GeoJSON 坐标系要求"
            description="GeoJSON 只允许 EPSG:4326，节点不会隐式转换坐标。"
          />
        )}
        <Form.Item
          name="outputColumnName"
          label="序列化输出字段"
          rules={[{ required: true, whitespace: true, message: '请输入输出字段名' }]}
        >
          <Input placeholder={format === 'WKB' ? 'geometry_wkb' : `geometry_${format.toLowerCase()}`} />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 parcels_serialized" />
        </Form.Item>
        <Typography.Text type="secondary">
          不做精度裁剪、EWKT/EWKB 或隐式 CRS 转换。
        </Typography.Text>
      </Form>
    </Space>
  );
};

export default GeometrySerializeInspector;
