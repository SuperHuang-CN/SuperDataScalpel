import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Segmented, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometryBufferConfiguration,
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

const fingerprint = (value: GeometryBufferConfiguration) => JSON.stringify(value);

const GeometryBufferInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometryBuffer>) => {
  const [form] = Form.useForm<GeometryBufferConfiguration>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const mode = Form.useWatch('mode', form) ?? 'PLANAR';
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
  const isWgs84 = geometryColumn?.geometry?.crs.authority === 'EPSG'
    && geometryColumn.geometry.crs.code === 4326
    && geometryColumn.geometry.dimension === 'XY';

  const toConfiguration = (
    values: GeometryBufferConfiguration,
  ): GeometryBufferConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    geometryColumnName: values.geometryColumnName ?? '',
    outputColumnName: values.outputColumnName?.trim() ?? '',
    distance: values.distance ?? 0,
    mode: values.mode ?? 'PLANAR',
  });
  const submit = (values: GeometryBufferConfiguration) => {
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
        title="输出固定为 MultiPolygon"
        description="Buffer 结果追加为新的 MULTIPOLYGON 字段，来源 Geometry 保持不变。首版只支持正距离。"
      />
      <Form<GeometryBufferConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
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
          help={sourceTableMissing ? '原来源表已失效，Buffer 配置仍被保留。' : undefined}
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
        <Form.Item name="mode" label="距离模式" rules={[{ required: true }]}>
          <Segmented
            block
            options={[
              { value: 'PLANAR', label: '平面' },
              { value: 'SPHEROID', label: 'WGS84 椭球' },
            ]}
          />
        </Form.Item>
        {geometryColumn && mode === 'PLANAR' && isWgs84 && (
          <Alert showIcon type="warning" title="EPSG:4326 的平面距离单位是角度" />
        )}
        {geometryColumn && mode === 'SPHEROID' && !isWgs84 && (
          <Alert showIcon type="error" title="椭球 Buffer 仅支持 EPSG:4326 XY" />
        )}
        <Form.Item
          name="distance"
          label={mode === 'SPHEROID' ? 'Buffer 距离（米）' : 'Buffer 距离（来源 CRS 坐标单位）'}
          rules={[
            { required: true, message: '请输入 Buffer 距离' },
            {
              validator: async (_, value: number | null) => {
                if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
                  throw new Error('Buffer 距离必须大于 0');
                }
              },
            },
          ]}
        >
          <InputNumber min={Number.MIN_VALUE} precision={8} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="outputColumnName"
          label="Buffer 结果字段"
          rules={[{ required: true, whitespace: true, message: '请输入结果字段名' }]}
        >
          <Input placeholder="buffer_geometry" />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 service_areas" />
        </Form.Item>
        <Typography.Text type="secondary">
          节点不做隐式 CRS 转换。需要其他单位时，请先连接空间转换节点。
        </Typography.Text>
      </Form>
    </Space>
  );
};

export default GeometryBufferInspector;
