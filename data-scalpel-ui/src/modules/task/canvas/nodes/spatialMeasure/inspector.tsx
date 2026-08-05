import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS,
  CanvasNodeType,
  type CanvasColumnSchema,
  type SpatialMeasureConfiguration,
  type SpatialMeasurement,
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

interface SpatialMeasureFormValues {
  sourceTableName: string;
  outputTableName: string;
}

type MeasurementKind = SpatialMeasurement['kind'];

const measurementKindOptions: Array<{ value: MeasurementKind; label: string }> = [
  { value: 'AREA', label: 'AREA · 面积' },
  { value: 'LENGTH', label: 'LENGTH · 长度' },
  { value: 'PERIMETER', label: 'PERIMETER · 周长' },
  { value: 'DISTANCE', label: 'DISTANCE · 两字段距离' },
  { value: 'X', label: 'X · Point 横坐标' },
  { value: 'Y', label: 'Y · Point 纵坐标' },
];

const defaultMeasurement = (
  kind: MeasurementKind,
  columns: CanvasColumnSchema[],
): SpatialMeasurement => {
  const first = columns[0]?.name ?? '';
  if (kind === 'DISTANCE') {
    return {
      kind,
      leftGeometryColumnName: first,
      rightGeometryColumnName: columns[1]?.name ?? first,
      mode: 'PLANAR',
      outputColumnName: 'distance',
    };
  }
  if (kind === 'X' || kind === 'Y') {
    return {
      kind,
      geometryColumnName: first,
      outputColumnName: kind.toLowerCase(),
    };
  }
  return {
    kind,
    geometryColumnName: first,
    mode: 'PLANAR',
    outputColumnName: kind.toLowerCase(),
  };
};

const acceptedKind = (measurement: SpatialMeasurement, column: CanvasColumnSchema) => {
  const geometryKind = column.geometry?.kind;
  if (measurement.kind === 'AREA' || measurement.kind === 'PERIMETER') {
    return geometryKind === 'POLYGON' || geometryKind === 'MULTIPOLYGON';
  }
  if (measurement.kind === 'LENGTH') {
    return geometryKind === 'LINESTRING' || geometryKind === 'MULTILINESTRING';
  }
  if (measurement.kind === 'X' || measurement.kind === 'Y') return geometryKind === 'POINT';
  return true;
};

const columnNameOf = (measurement: SpatialMeasurement) => (
  'geometryColumnName' in measurement ? measurement.geometryColumnName : ''
);

const isWgs84 = (column: CanvasColumnSchema | undefined) => (
  column?.geometry?.crs.authority === 'EPSG'
  && column.geometry.crs.code === 4326
  && column.geometry.dimension === 'XY'
);

const hasSameSpatialReference = (
  left: CanvasColumnSchema | undefined,
  right: CanvasColumnSchema | undefined,
) => Boolean(
  left?.geometry
  && right?.geometry
  && left.geometry.crs.authority === right.geometry.crs.authority
  && left.geometry.crs.code === right.geometry.crs.code
  && left.geometry.dimension === right.geometry.dimension,
);

const fingerprint = (value: SpatialMeasureConfiguration) => JSON.stringify(value);

const SpatialMeasureInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialMeasure>) => {
  const [form] = Form.useForm<SpatialMeasureFormValues>();
  const [measurements, setMeasurements] = useState<SpatialMeasurement[]>(
    node.configuration.measurements,
  );
  const sourceTableName = Form.useWatch('sourceTableName', form)
    ?? node.configuration.sourceTableName;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const geometryColumns = spatialGeometryColumns(sourceTable);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);

  const configuration = (
    values: SpatialMeasureFormValues,
    items: SpatialMeasurement[],
  ): SpatialMeasureConfiguration => ({
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    measurements: items,
  });

  const markDirty = (items = measurements) => {
    onDirtyChange(
      fingerprint(configuration(form.getFieldsValue(), items))
        !== fingerprint(node.configuration),
    );
  };
  const updateMeasurements = (items: SpatialMeasurement[]) => {
    setMeasurements(items);
    markDirty(items);
  };
  const updateMeasurement = (index: number, item: SpatialMeasurement) => {
    updateMeasurements(measurements.map((candidate, itemIndex) => (
      itemIndex === index ? item : candidate
    )));
  };
  const move = (from: number, to: number) => {
    const next = [...measurements];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    updateMeasurements(next);
  };
  const submit = (values: SpatialMeasureFormValues) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: configuration(values, measurements),
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
        title="平面与椭球测量"
        description="PLANAR 使用 CRS 坐标单位；SPHEROID 只支持 EPSG:4326，并输出米或平方米。节点逐行无状态，不改变事件时间和 Watermark。"
      />
      <Form<SpatialMeasureFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={{
          sourceTableName: node.configuration.sourceTableName,
          outputTableName: node.configuration.outputTableName,
        }}
        onFinish={submit}
        onValuesChange={() => markDirty()}
      >
        <Form.Item
          name="sourceTableName"
          label="来源表"
          rules={[{ required: true, message: '请选择来源表' }]}
          validateStatus={sourceTableMissing ? 'error' : undefined}
          help={sourceTableMissing ? '原来源表已失效，全部测量项仍被保留。' : undefined}
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
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 parcels_measured" />
        </Form.Item>
      </Form>

      <div className="canvas-processor-section-header">
        <span>
          <Typography.Text strong>测量项</Typography.Text>
          <Typography.Text type="secondary">
            {` · ${measurements.length}/${CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS}`}
          </Typography.Text>
        </span>
        <Button
          size="small"
          icon={<PlusOutlined />}
          disabled={measurements.length >= CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS}
          onClick={() => updateMeasurements([
            ...measurements,
            defaultMeasurement('AREA', geometryColumns),
          ])}
        >
          添加测量
        </Button>
      </div>
      {measurements.length === 0 && (
        <Typography.Text type="secondary">
          至少添加一个面积、长度、周长、距离或坐标测量。
        </Typography.Text>
      )}
      <div className="canvas-processor-rule-list">
        {measurements.map((measurement, index) => {
          const distance = measurement.kind === 'DISTANCE';
          const sourceName = columnNameOf(measurement);
          const sourceColumn = geometryColumns.find((column) => column.name === sourceName);
          const leftColumn = distance
            ? geometryColumns.find(
              (column) => column.name === measurement.leftGeometryColumnName,
            )
            : undefined;
          const rightColumn = distance
            ? geometryColumns.find(
              (column) => column.name === measurement.rightGeometryColumnName,
            )
            : undefined;
          const invalidSource = distance
            ? Boolean(
              measurement.leftGeometryColumnName && !leftColumn
              || measurement.rightGeometryColumnName && !rightColumn,
            )
            : Boolean(
              sourceName
              && (!sourceColumn || !acceptedKind(measurement, sourceColumn)),
            );
          const mode = 'mode' in measurement ? measurement.mode : null;
          const angularWarning = mode === 'PLANAR' && (distance
            ? isWgs84(leftColumn) || isWgs84(rightColumn)
            : isWgs84(sourceColumn));
          const spheroidInvalid = mode === 'SPHEROID' && (distance
            ? Boolean(
              leftColumn && !isWgs84(leftColumn)
              || rightColumn && !isWgs84(rightColumn),
            )
            : Boolean(sourceColumn && !isWgs84(sourceColumn)));
          const distanceReferenceMismatch = distance
            && Boolean(leftColumn && rightColumn)
            && !hasSameSpatialReference(leftColumn, rightColumn);
          const filteredOptions = spatialColumnOptions(
            geometryColumns,
            sourceName,
            (column) => acceptedKind(measurement, column),
          );
          return (
            <Card
              size="small"
              key={index}
              className={`canvas-processor-rule-card${
                invalidSource || spheroidInvalid || distanceReferenceMismatch
                  ? ' is-invalid'
                  : ''
              }`}
              title={(
                <Space size={6}>
                  <Tag color="purple">{index + 1}</Tag>
                  <span>{measurement.kind}</span>
                  {invalidSource && <Tag color="error">字段已失效</Tag>}
                  {distanceReferenceMismatch && <Tag color="error">CRS 不兼容</Tag>}
                  {angularWarning && <Tag color="warning">角度单位</Tag>}
                </Space>
              )}
              extra={(
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<UpOutlined />}
                    aria-label={`上移空间测量 ${index + 1}`}
                    disabled={index === 0}
                    onClick={() => move(index, index - 1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<DownOutlined />}
                    aria-label={`下移空间测量 ${index + 1}`}
                    disabled={index === measurements.length - 1}
                    onClick={() => move(index, index + 1)}
                  />
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    aria-label={`删除空间测量 ${index + 1}`}
                    onClick={() => updateMeasurements(
                      measurements.filter((_, itemIndex) => itemIndex !== index),
                    )}
                  />
                </Space>
              )}
            >
              <Space orientation="vertical" size={8} className="canvas-full-width">
                <Select
                  value={measurement.kind}
                  options={measurementKindOptions}
                  onChange={(kind: MeasurementKind) => updateMeasurement(
                    index,
                    defaultMeasurement(kind, geometryColumns),
                  )}
                />
                {measurement.kind === 'DISTANCE' ? (
                  <>
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={measurement.leftGeometryColumnName || undefined}
                      status={measurement.leftGeometryColumnName
                        && (!leftColumn
                          || mode === 'SPHEROID' && !isWgs84(leftColumn)
                          || distanceReferenceMismatch)
                        ? 'error' : undefined}
                      options={spatialColumnOptions(
                        geometryColumns,
                        measurement.leftGeometryColumnName,
                        () => true,
                      )}
                      placeholder="左侧 Geometry 字段"
                      onChange={(value) => updateMeasurement(index, {
                        ...measurement,
                        leftGeometryColumnName: value,
                      })}
                    />
                    <Select
                      showSearch
                      optionFilterProp="label"
                      value={measurement.rightGeometryColumnName || undefined}
                      status={measurement.rightGeometryColumnName
                        && (!rightColumn
                          || mode === 'SPHEROID' && !isWgs84(rightColumn)
                          || distanceReferenceMismatch)
                        ? 'error' : undefined}
                      options={spatialColumnOptions(
                        geometryColumns,
                        measurement.rightGeometryColumnName,
                        () => true,
                      )}
                      placeholder="右侧 Geometry 字段"
                      onChange={(value) => updateMeasurement(index, {
                        ...measurement,
                        rightGeometryColumnName: value,
                      })}
                    />
                  </>
                ) : (
                  <Select
                    showSearch
                    optionFilterProp="label"
                    value={measurement.geometryColumnName || undefined}
                    status={invalidSource ? 'error' : undefined}
                    options={filteredOptions}
                    placeholder="选择 Geometry 字段"
                    onChange={(value) => updateMeasurement(index, {
                      ...measurement,
                      geometryColumnName: value,
                    })}
                  />
                )}
                {'mode' in measurement && (
                  <Select
                    value={measurement.mode}
                    status={spheroidInvalid ? 'error' : undefined}
                    options={[
                      { value: 'PLANAR', label: 'PLANAR · CRS 坐标单位' },
                      { value: 'SPHEROID', label: 'SPHEROID · 米 / 平方米' },
                    ]}
                    onChange={(value: 'PLANAR' | 'SPHEROID') => updateMeasurement(index, {
                      ...measurement,
                      mode: value,
                    })}
                  />
                )}
                {spheroidInvalid && (
                  <Typography.Text type="danger">
                    SPHEROID 只支持 EPSG:4326，原配置已保留。
                  </Typography.Text>
                )}
                {distanceReferenceMismatch && (
                  <Typography.Text type="danger">
                    距离两侧必须维度一致；PLANAR 还要求 CRS 完全一致。
                  </Typography.Text>
                )}
                {angularWarning && (
                  <Typography.Text type="warning">
                    EPSG:4326 的 PLANAR 结果使用角度或角度平方。
                  </Typography.Text>
                )}
                <Input
                  value={measurement.outputColumnName}
                  status={!measurement.outputColumnName.trim() ? 'error' : undefined}
                  placeholder="输出字段名"
                  onChange={(event) => updateMeasurement(index, {
                    ...measurement,
                    outputColumnName: event.target.value,
                  })}
                />
              </Space>
            </Card>
          );
        })}
      </div>
    </Space>
  );
};

export default SpatialMeasureInspector;
