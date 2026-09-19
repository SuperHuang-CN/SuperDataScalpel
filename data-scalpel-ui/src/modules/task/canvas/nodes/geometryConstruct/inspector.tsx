import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Select, Space, Typography } from 'antd';
import { useImperativeHandle } from 'react';
import {
  CanvasNodeType,
  type GeometryConstructConfiguration,
  type GeometryConstructSource,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';

type SourceKind = GeometryConstructSource['kind'];
type TargetKind = NonNullable<GeometryConstructConfiguration['targetGeometry']>['kind'];

interface GeometryConstructFormValues {
  sourceTableName: string;
  outputTableName: string;
  outputColumnName: string;
  sourceKind: SourceKind;
  sourceColumnName: string;
  xColumnName: string;
  yColumnName: string;
  targetKind: TargetKind | null;
  targetEpsgCode: number | null;
}

const sourceOptions = [
  { value: 'WKT', label: 'WKT · STRING 字段' },
  { value: 'WKB', label: 'WKB · BINARY 字段' },
  { value: 'GEOJSON', label: 'GeoJSON · STRING 字段' },
  { value: 'POINT_FROM_XY', label: 'Point from X/Y · 数值字段' },
];

const targetKindOptions: Array<{ value: TargetKind; label: string }> = [
  'POINT',
  'LINESTRING',
  'POLYGON',
  'MULTIPOINT',
  'MULTILINESTRING',
  'MULTIPOLYGON',
  'GEOMETRYCOLLECTION',
].map((kind) => ({ value: kind as TargetKind, label: kind }));

const initialSourceFields = (source: GeometryConstructSource) => ({
  sourceKind: source.kind,
  sourceColumnName: 'columnName' in source ? source.columnName : '',
  xColumnName: 'xColumnName' in source ? source.xColumnName : '',
  yColumnName: 'yColumnName' in source ? source.yColumnName : '',
});

const toConfiguration = (
  values: GeometryConstructFormValues,
): GeometryConstructConfiguration => {
  const source: GeometryConstructSource = values.sourceKind === 'POINT_FROM_XY'
    ? {
      kind: 'POINT_FROM_XY',
      xColumnName: values.xColumnName ?? '',
      yColumnName: values.yColumnName ?? '',
    }
    : {
      kind: values.sourceKind,
      columnName: values.sourceColumnName ?? '',
    };
  return {
    sourceTableName: values.sourceTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    outputColumnName: values.outputColumnName?.trim() ?? '',
    source,
    targetGeometry: values.targetKind
      && Number.isInteger(values.targetEpsgCode)
      && (values.targetEpsgCode ?? 0) > 0
      ? {
        kind: values.sourceKind === 'POINT_FROM_XY' ? 'POINT' : values.targetKind,
        crs: { authority: 'EPSG', code: values.targetEpsgCode as number },
        dimension: 'XY',
      }
      : null,
  };
};

const fingerprint = (value: GeometryConstructConfiguration) => JSON.stringify(value);

const GeometryConstructInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.GeometryConstruct>) => {
  const [form] = Form.useForm<GeometryConstructFormValues>();
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const sourceKind = Form.useWatch('sourceKind', form) ?? 'WKT';
  const sourceColumnName = Form.useWatch('sourceColumnName', form) ?? '';
  const xColumnName = Form.useWatch('xColumnName', form) ?? '';
  const yColumnName = Form.useWatch('yColumnName', form) ?? '';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const sourceTableMissing = Boolean(sourceTableName && validation && !sourceTable);
  const columns = sourceTable?.columns ?? [];
  const initialValues: GeometryConstructFormValues = {
    sourceTableName: node.configuration.sourceTableName,
    outputTableName: node.configuration.outputTableName,
    outputColumnName: node.configuration.outputColumnName,
    ...initialSourceFields(node.configuration.source),
    targetKind: node.configuration.targetGeometry?.kind ?? null,
    targetEpsgCode: node.configuration.targetGeometry?.crs.code ?? null,
  };

  const submit = (values: GeometryConstructFormValues) => {
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

  const scalarType = sourceKind === 'WKB' ? 'BINARY' : 'STRING';
  const scalarOptions = spatialColumnOptions(
    columns,
    sourceColumnName,
    (column) => column.fieldType === scalarType,
  );
  const numeric = new Set(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Alert
        showIcon
        type="info"
        title="显式声明空间类型"
        description="节点忽略来源内容中的 CRS，使用所选 EPSG 和 XY 设置 SRID；畸形内容或实际 GeometryKind 不一致会让任务失败。"
      />
      <Form<GeometryConstructFormValues>
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
          help={sourceTableMissing ? '原来源表已失效，配置仍被保留。' : undefined}
        >
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)}
            placeholder={validation ? '选择来源表' : '等待 Task Engine 计算上游表'}
          />
        </Form.Item>
        <Form.Item name="sourceKind" label="构造来源" rules={[{ required: true }]}>
          <Select
            options={sourceOptions}
            onChange={(kind: SourceKind) => {
              if (kind === 'POINT_FROM_XY') form.setFieldValue('targetKind', 'POINT');
            }}
          />
        </Form.Item>
        {sourceKind === 'POINT_FROM_XY' ? (
          <Space size={8} align="start" className="canvas-full-width">
            <Form.Item
              name="xColumnName"
              label="X 字段"
              rules={[{ required: true, message: '请选择 X 字段' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={spatialColumnOptions(columns, xColumnName, (column) => numeric.has(column.fieldType))}
                placeholder="选择数值字段"
              />
            </Form.Item>
            <Form.Item
              name="yColumnName"
              label="Y 字段"
              rules={[{ required: true, message: '请选择 Y 字段' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={spatialColumnOptions(columns, yColumnName, (column) => numeric.has(column.fieldType))}
                placeholder="选择数值字段"
              />
            </Form.Item>
          </Space>
        ) : (
          <Form.Item
            name="sourceColumnName"
            label={`${sourceKind} 来源字段`}
            rules={[{ required: true, message: '请选择来源字段' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={scalarOptions}
              placeholder={`选择 ${scalarType} 字段`}
            />
          </Form.Item>
        )}
        <Space size={8} align="start" className="canvas-full-width">
          <Form.Item name="targetKind" label="目标 GeometryKind" rules={[{ required: true }]}>
            <Select
              disabled={sourceKind === 'POINT_FROM_XY'}
              options={targetKindOptions}
              placeholder="选择具体类型"
            />
          </Form.Item>
          <Form.Item
            name="targetEpsgCode"
            label="目标 CRS"
            rules={[{ required: true, message: '请输入 EPSG code' }]}
          >
            <InputNumber min={1} precision={0} addonBefore="EPSG" placeholder="4326" />
          </Form.Item>
        </Space>
        <Form.Item
          name="outputColumnName"
          label="Geometry 输出字段"
          rules={[{ required: true, whitespace: true, message: '请输入输出字段名' }]}
        >
          <Input placeholder="例如 geometry" />
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[{ required: true, whitespace: true, message: '请输入输出表名' }]}
        >
          <Input placeholder="例如 orders_geometry" />
        </Form.Item>
        {sourceTable && (
          <Typography.Text type="secondary">
            保留上游全部 {sourceTable.columns.length} 个字段，并在末尾追加 Geometry 字段。
          </Typography.Text>
        )}
      </Form>
    </Space>
  );
};

export default GeometryConstructInspector;
