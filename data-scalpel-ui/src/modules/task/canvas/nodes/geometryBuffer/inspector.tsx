import { CompactAlert as Alert, ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Segmented, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  type CanvasColumnSchema,
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
import {
  spatialDistanceUnitOptions,
  spatialUnitHelp,
} from '../spatialUnits';

const fingerprint = (value: GeometryBufferConfiguration) => JSON.stringify(value);
const numericDistanceField = (column: CanvasColumnSchema) => (
  ['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']
    .includes(column.fieldType)
);

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
  const distanceSource = Form.useWatch('distanceSource', form) ?? 'CONSTANT';
  const distanceFieldName = Form.useWatch('distanceFieldName', form) ?? '';
  const distanceUnit = Form.useWatch('distanceUnit', form)
    ?? (mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT');
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
  const distanceFieldMissing = Boolean(
    distanceSource === 'FIELD'
    && distanceFieldName
    && sourceTable
    && !sourceTable.columns.some((column) => (
      column.name === distanceFieldName && numericDistanceField(column)
    )),
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
    distanceUnit: values.distanceUnit
      ?? (values.mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT'),
    distanceSource: values.distanceSource ?? 'CONSTANT',
    distanceFieldName: values.distanceFieldName ?? null,
    distanceExpression: values.distanceExpression ?? null,
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
        description="每行可使用固定值、数值字段或受控表达式计算正距离。结果追加为 MULTIPOLYGON，来源 Geometry 保持不变。"
      />
      <Form<GeometryBufferConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={{
          ...node.configuration,
          distanceUnit: node.configuration.distanceUnit
            ?? (node.configuration.mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT'),
          distanceSource: node.configuration.distanceSource ?? 'CONSTANT',
          distanceFieldName: node.configuration.distanceFieldName ?? null,
          distanceExpression: node.configuration.distanceExpression ?? null,
        }}
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
        {geometryColumn && mode === 'PLANAR' && isWgs84
          && distanceUnit === 'SOURCE_CRS_UNIT' && (
          <Alert showIcon type="warning" title="EPSG:4326 的来源 CRS 单位是角度" />
        )}
        {geometryColumn && mode === 'PLANAR' && isWgs84
          && distanceUnit !== 'SOURCE_CRS_UNIT' && (
          <Alert showIcon type="error" title="地理 CRS 的平面 Buffer 不能把线性单位直接换算为角度" />
        )}
        {geometryColumn && mode === 'SPHEROID' && !isWgs84 && (
          <Alert showIcon type="error" title="椭球 Buffer 仅支持 EPSG:4326 XY" />
        )}
        {mode === 'SPHEROID' && distanceUnit === 'SOURCE_CRS_UNIT' && (
          <Alert showIcon type="error" title="椭球 Buffer 需要明确的线性距离单位" />
        )}
        <Form.Item name="distanceSource" label="距离来源" rules={[{ required: true }]}>
          <Segmented
            block
            options={[
              { value: 'CONSTANT', label: '固定值' },
              { value: 'FIELD', label: '字段' },
              { value: 'EXPRESSION', label: '表达式' },
            ]}
          />
        </Form.Item>
        {distanceSource === 'CONSTANT' && (
          <Form.Item
            name="distance"
            label="Buffer 距离"
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
        )}
        {distanceSource === 'FIELD' && (
          <Form.Item
            name="distanceFieldName"
            label="距离字段"
            rules={[{ required: true, message: '请选择数值字段' }]}
            validateStatus={distanceFieldMissing ? 'error' : undefined}
            help={distanceFieldMissing ? '原距离字段已失效或不是数值类型，配置仍被保留。' : undefined}
          >
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              disabled={!sourceTable}
              options={spatialColumnOptions(
                sourceTable?.columns ?? [],
                distanceFieldName,
                numericDistanceField,
              )}
              placeholder="选择逐行距离字段"
            />
          </Form.Item>
        )}
        {distanceSource === 'EXPRESSION' && (
          <Form.Item
            name="distanceExpression"
            label={(
              <span className="canvas-inspector-field-label">
                距离表达式
                <ContextHelp
                  ariaLabel="Buffer 距离表达式说明"
                  content="使用当前行字段组成一个确定性的 Spark 数值表达式，例如 radius * 1.2 或 coalesce(radius, 100)。不允许 SELECT、聚合、窗口、子查询或多条语句。"
                />
              </span>
            )}
            rules={[{ required: true, whitespace: true, message: '请输入逐行数值表达式' }]}
          >
            <Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} placeholder="例如 coalesce(radius, 100)" />
          </Form.Item>
        )}
        <Form.Item
          name="distanceUnit"
          label={(
            <span className="canvas-inspector-field-label">
              距离单位
              <ContextHelp
                ariaLabel="Buffer 距离单位说明"
                content={`${spatialUnitHelp} 单位作用于固定值、字段值或表达式结果。平面模式换算到投影 CRS 轴单位；地理 CRS 的平面模式只能使用来源角度单位；椭球模式换算为米。`}
              />
            </span>
          )}
          rules={[{ required: true, message: '请选择距离单位' }]}
        >
          <Select options={spatialDistanceUnitOptions} />
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
          NULL 动态距离产生 NULL Buffer；非正数、NaN 或无穷值会使任务失败。节点不转换 Geometry CRS，切换来源或单位不会清除其他草稿值。
        </Typography.Text>
      </Form>
    </Space>
  );
};

export default GeometryBufferInspector;
