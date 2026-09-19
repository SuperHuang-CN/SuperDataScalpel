import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, InputNumber, Select, Space, Switch, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CANVAS_SPATIAL_DESCRIBE_DATASET_MAX_SAMPLE_SIZE,
  CanvasNodeType,
  type SpatialDescribeDatasetConfiguration,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialColumnOptions, spatialTableOptions } from '../spatialInspectorOptions';

const fingerprint = (value: SpatialDescribeDatasetConfiguration) => JSON.stringify(value);

const normalize = (
  value: SpatialDescribeDatasetConfiguration,
): SpatialDescribeDatasetConfiguration => ({
  sourceTableName: value.sourceTableName ?? '',
  geometryColumnName: value.geometryColumnName ?? '',
  statisticsTableName: value.statisticsTableName?.trim() ?? '',
  descriptionTableName: value.descriptionTableName?.trim() ?? '',
  sampleSize: Number.isInteger(value.sampleSize) ? value.sampleSize : 0,
  sampleTableName: value.sampleTableName?.trim() ?? '',
  extentOutput: value.extentOutput === true,
  extentTableName: value.extentTableName?.trim() ?? '',
});

const SpatialDescribeDatasetInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialDescribeDataset>) => {
  const [form] = Form.useForm<SpatialDescribeDatasetConfiguration>();
  const [sampleDraftSize, setSampleDraftSize] = useState(
    node.configuration.sampleSize > 0 ? node.configuration.sampleSize : 100,
  );
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const geometryColumnName = Form.useWatch('geometryColumnName', form) ?? '';
  const sampleSize = Form.useWatch('sampleSize', { form, preserve: true }) ?? 0;
  const extentOutput = Form.useWatch('extentOutput', { form, preserve: true }) === true;
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find(table => table.name === sourceTableName);
  const profileFieldCount = sourceTable?.columns.filter(column => (
    column.fieldType !== 'GEOMETRY' && column.fieldType !== 'BINARY'
  )).length ?? 0;

  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(normalize(node.configuration)));
  };
  const submit = (value: SpatialDescribeDatasetConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(value) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({
    apply: async () => {
      void form.validateFields().catch(() => undefined);
      submit(form.getFieldsValue(true));
      return true;
    },
  }));
  const update = <K extends keyof SpatialDescribeDatasetConfiguration>(
    name: K,
    value: SpatialDescribeDatasetConfiguration[K],
  ) => {
    form.setFieldValue(name, value);
    markDirty({ ...form.getFieldsValue(true), [name]: value });
  };

  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <Form<SpatialDescribeDatasetConfiguration>
      form={form}
      layout="vertical"
      autoComplete="off"
      initialValues={normalize(node.configuration)}
      onFinish={() => submit(form.getFieldsValue(true))}
      onValuesChange={() => markDirty()}
    >
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!validation}
            options={spatialTableOptions(tables, sourceTableName)} placeholder="选择需要剖析的逻辑表" />
        </Form.Item>
        <Form.Item name="geometryColumnName" label={<span className="canvas-inspector-field-label">
          Geometry（可选）<ContextHelp ariaLabel="描述数据集 Geometry 说明"
            content="选择后在描述结果中统计非空数量与 XY 范围；只有启用范围结果时才是必填。不会修改 Geometry 或 CRS。" />
        </span>}>
          <Select allowClear showSearch optionFilterProp="label" disabled={!sourceTable}
            options={spatialColumnOptions(
              sourceTable?.columns ?? [], geometryColumnName,
              column => column.fieldType === 'GEOMETRY',
            )} placeholder="不做空间描述" />
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>必选结果</Typography.Text>
          <Tag>{profileFieldCount} 个可统计字段</Tag>
          <ContextHelp ariaLabel="字段统计结果说明"
            content="数值字段输出 Count、Sum、Mean、Min、Max、Range、总体标准差与方差；时间字段输出 Min、Max 和毫秒范围；字符串与布尔字段输出确定性的代表值。Geometry 和 Binary 不进入逐字段统计。" />
        </Space>
      </div>
      <div className="canvas-spatial-pair-grid">
        <Form.Item name="statisticsTableName" label="字段统计表" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 city_field_statistics" />
        </Form.Item>
        <Form.Item name="descriptionTableName" label="数据集描述表" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 city_description" />
        </Form.Item>
      </div>

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>样本结果</Typography.Text>
          <Typography.Text type="secondary">{sampleSize > 0 ? `${sampleSize} 行` : '未启用'}</Typography.Text>
          <ContextHelp ariaLabel="描述数据集样本说明"
            content="样本使用 Spark limit，不增加隐藏排序。输入重分区、文件切分或物理顺序变化后不保证得到同一批记录。" />
        </Space>
        <Switch size="small" checked={sampleSize > 0} aria-label="启用样本结果"
          onChange={checked => {
            if (checked) update('sampleSize', Math.max(1, sampleDraftSize));
            else {
              if (sampleSize > 0) setSampleDraftSize(sampleSize);
              update('sampleSize', 0);
            }
          }} />
      </div>
      {sampleSize > 0 && <div className="canvas-spatial-pair-grid">
        <Form.Item name="sampleSize" label="样本数量" rules={[{ required: true }]}>
          <InputNumber min={1} max={CANVAS_SPATIAL_DESCRIBE_DATASET_MAX_SAMPLE_SIZE}
            precision={0} style={{ width: '100%' }} onChange={value => {
              if (typeof value === 'number') setSampleDraftSize(value);
            }} />
        </Form.Item>
        <Form.Item name="sampleTableName" label="样本表名" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 city_sample" />
        </Form.Item>
      </div>}

      <div className="canvas-processor-section-header">
        <Space size={6}><Typography.Text strong>空间范围</Typography.Text>
          <Typography.Text type="secondary">{extentOutput ? '输出 XY Envelope' : '未启用'}</Typography.Text>
          <ContextHelp ariaLabel="描述数据集空间范围说明"
            content="输出所选 Geometry 全部非空非 Empty 要素的 XY 外包矩形 Polygon，继承 CRS、固定为 XY；不是凸包。" />
        </Space>
        <Switch size="small" checked={extentOutput} aria-label="启用空间范围结果"
          onChange={checked => update('extentOutput', checked)} />
      </div>
      {extentOutput && <Form.Item name="extentTableName" label="范围表名"
        rules={[{ required: true, whitespace: true }]} extra={!geometryColumnName ? '请先选择 Geometry 字段' : undefined}>
        <Input placeholder="例如 city_extent" />
      </Form.Item>}
    </Form>
  </Space>;
};

export default SpatialDescribeDatasetInspector;
