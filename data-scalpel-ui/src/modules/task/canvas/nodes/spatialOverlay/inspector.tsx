import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { SettingOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type JoinOutputColumn,
  type SpatialOverlayConfiguration,
  type SpatialOverlayOperation,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { JoinOutputColumnsEditor } from '../../components/JoinOutputColumnsEditor';
import { suggestJoinOutputColumns } from '../../components/joinOutputColumns';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import { overlayCombinationSupported, overlayFamily, overlayOperationLabels, overlayOperations,
  overlayResultLabel, usesOverlayFamily } from './geometryPolicy';

const fingerprint = (value: SpatialOverlayConfiguration) => JSON.stringify(value);

const projectForOperation = (
  columns: JoinOutputColumn[],
  operation: SpatialOverlayOperation | null,
) => operation === 'ERASE'
  ? columns.map((column) => column.sourceSide === 'RIGHT' ? { ...column, included: false } : column)
  : columns;

const SpatialOverlayInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialOverlay>) => {
  const [form] = Form.useForm<SpatialOverlayConfiguration>();
  const [projectionOpen, setProjectionOpen] = useState(false);
  const leftTableName = Form.useWatch('leftTableName', form) ?? '';
  const rightTableName = Form.useWatch('rightTableName', form) ?? '';
  const leftGeometryColumnName = Form.useWatch('leftGeometryColumnName', form) ?? '';
  const rightGeometryColumnName = Form.useWatch('rightGeometryColumnName', form) ?? '';
  const operation = Form.useWatch('operation', form) ?? null;
  const outputColumns: JoinOutputColumn[] = Form.useWatch('outputColumns', { form, preserve: true }) ?? [];
  const geometryPolicy = Form.useWatch('geometryPolicy', { form, preserve: true });
  const familyGeometry = usesOverlayFamily({ operation, geometryPolicy });
  const tables = validation?.inputTables ?? [];
  const leftTable = tables.find((table) => table.name === leftTableName);
  const rightTable = tables.find((table) => table.name === rightTableName);
  const leftGeometry = spatialGeometryColumns(leftTable)
    .find((column) => column.name === leftGeometryColumnName);
  const rightGeometry = spatialGeometryColumns(rightTable)
    .find((column) => column.name === rightGeometryColumnName);
  const mismatch = Boolean(leftGeometry?.geometry && rightGeometry?.geometry
    && (leftGeometry.geometry.crs.authority !== rightGeometry.geometry.crs.authority
      || leftGeometry.geometry.crs.code !== rightGeometry.geometry.crs.code
      || leftGeometry.geometry.dimension !== rightGeometry.geometry.dimension));
  const invalidEraseFields = operation === 'ERASE'
    ? outputColumns.filter((column) => column.included && column.sourceSide === 'RIGHT').length : 0;
  const leftFamily = overlayFamily(leftGeometry?.geometry?.kind);
  const rightFamily = overlayFamily(rightGeometry?.geometry?.kind);
  const invalidCombination = Boolean(familyGeometry && operation && leftGeometry && rightGeometry
    && !overlayCombinationSupported(operation, leftFamily, rightFamily));
  const invalidPolicy = geometryPolicy === 'LEGACY_GEOMETRY'
    && (operation === 'IDENTITY' || operation === 'SYMMETRICAL_DIFFERENCE');

  const normalize = (values: SpatialOverlayConfiguration): SpatialOverlayConfiguration => ({
    leftTableName: values.leftTableName ?? '',
    leftGeometryColumnName: values.leftGeometryColumnName ?? '',
    rightTableName: values.rightTableName ?? '',
    rightGeometryColumnName: values.rightGeometryColumnName ?? '',
    operation: values.operation ?? null,
    ...('geometryPolicy' in values ? { geometryPolicy: values.geometryPolicy ?? null } : {}),
    outputTableName: values.outputTableName?.trim() ?? '',
    outputGeometryColumnName: values.outputGeometryColumnName?.trim() ?? '',
    outputColumns: (values.outputColumns ?? []).map((column) => ({
      ...column,
      outputColumnName: column.outputColumnName.trim(),
    })),
  });
  const markDirty = (values = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(values)) !== fingerprint(node.configuration));
  };
  const submit = (values: SpatialOverlayConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(values) });
    onDirtyChange(false);
  };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        void form.validateFields().catch(() => undefined);
        submit(form.getFieldsValue(true));
        return true;
      },
    }),
  );

  const suggestIfEmpty = (values: SpatialOverlayConfiguration) => {
    if ((values.outputColumns ?? []).length > 0) return;
    const left = tables.find((table) => table.name === values.leftTableName);
    const right = tables.find((table) => table.name === values.rightTableName);
    if (!left || !right || left.name === right.name) return;
    form.setFieldValue('outputColumns', projectForOperation(
      suggestJoinOutputColumns(left, right), values.operation ?? null,
    ));
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<SpatialOverlayConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => {
          suggestIfEmpty(form.getFieldsValue(true));
          markDirty();
        }}
      >
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="leftTableName" label="左侧图层" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, leftTableName)} />
          </Form.Item>
          <Form.Item name="rightTableName" label="右侧图层" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, rightTableName)} />
          </Form.Item>
          <Form.Item name="leftGeometryColumnName" label="左侧 Geometry" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              leftTable?.columns ?? [], leftGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY',
            )} />
          </Form.Item>
          <Form.Item name="rightGeometryColumnName" label="右侧 Geometry" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              rightTable?.columns ?? [], rightGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY',
            )} />
          </Form.Item>
        </div>
        {mismatch && <Typography.Text type="danger" className="canvas-field-inline-warning">
          两侧 CRS 或坐标维度不一致，请先进行空间转换。
        </Typography.Text>}
        <Form.Item
          name="operation"
          rules={[{ required: true }]}
          label={<span className="canvas-inspector-field-label">叠加方式<ContextHelp
            ariaLabel="空间叠加方式说明"
            content={<><p>相交：双方共同部分。擦除：左侧独有部分。联合：双方共同及独有部分。</p>
              <p>标识：保留左侧覆盖，交叠处附右属性，未匹配处右属性为 NULL。对称差：双方各自独有部分。</p>
              <p>相交允许任意点/线/面组合；擦除、对称差要求同家族；联合只允许面；标识要求同家族或右侧为面。</p>
              <p>交叠按左右要素配对输出，不合并同侧重叠要素；并非全局无重叠拓扑分区。Sedona/JTS 精度不等于 Esri 容差。</p></>}
          /></span>}
        >
          <Select aria-label="叠加方式" status={invalidCombination ? 'error' : undefined}
            options={overlayOperations.map(value => {
              const strict = usesOverlayFamily({ operation: value, geometryPolicy });
              const disabled = Boolean(strict && leftGeometry && rightGeometry
                && !overlayCombinationSupported(value, leftFamily, rightFamily));
              return { value, disabled, label: <Tooltip title={disabled ? '当前点/线/面家族组合不支持此方式' : undefined}>
                {overlayOperationLabels[value]}{disabled ? '（不适用）' : ''}</Tooltip> };
            })} />
        </Form.Item>
        <Form.Item label={<span className="canvas-inspector-field-label">几何输出<ContextHelp
          ariaLabel="叠加几何输出说明" content="图层家族模式输出 MultiPoint / MultiLineString / MultiPolygon，结果为 XY；低维接触片段不输出，NULL/空几何跳过，无效几何执行时报错，不自动修复。旧版保留通用 Geometry 和原三模式语义。标识和对称差必须使用图层家族模式。" /></span>}>
          <Select aria-label="叠加几何输出" value={geometryPolicy ?? (familyGeometry ? 'FAMILY_2D' : 'LEGACY_GEOMETRY')}
            status={invalidPolicy ? 'error' : undefined}
            options={[{ value: 'FAMILY_2D', label: '图层家族 · 二维多部件' },
              { value: 'LEGACY_GEOMETRY', label: '旧版 · 通用 Geometry',
                disabled: operation === 'IDENTITY' || operation === 'SYMMETRICAL_DIFFERENCE' }]}
            onChange={value => Modal.confirm({ title: '切换几何输出策略？',
              content: '这会改变允许的几何组合、结果家族及坐标维度。既有字段投影保留，请检查下游。',
              okText: '确认切换', onOk: () => { form.setFieldValue('geometryPolicy', value); markDirty(); },
            })} />
        </Form.Item>
        {familyGeometry && <Typography.Text type={invalidCombination ? 'danger' : 'secondary'}>
          {overlayResultLabel(operation, leftFamily, rightFamily)}
        </Typography.Text>}
        {invalidCombination && <Typography.Text type="danger">当前叠加方式不支持所选几何家族。</Typography.Text>}
        {invalidPolicy && <Typography.Text type="danger">请切换为图层家族二维输出；已有配置可以保存为草稿。</Typography.Text>}
        {invalidEraseFields > 0 && <Typography.Text type="danger" className="canvas-field-inline-warning">
          擦除模式不能输出右侧属性，当前有 {invalidEraseFields} 个字段需要排除。
        </Typography.Text>}
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="outputGeometryColumnName" label="结果 Geometry 字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
        </div>
        <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>输出字段</Typography.Text>
            <Tag>{outputColumns.filter((column) => column.included).length}/{outputColumns.length}</Tag>
          </Space>
          <Button size="small" aria-label="设置叠加输出字段" icon={<SettingOutlined />} onClick={() => setProjectionOpen(true)}>设置</Button>
        </div>
        <Modal open={projectionOpen} width={860} title="设置空间叠加输出字段"
          okText="完成" cancelText="关闭" onOk={() => setProjectionOpen(false)}
          onCancel={() => setProjectionOpen(false)}>
          <JoinOutputColumnsEditor
            left={leftTable}
            right={rightTable}
            conditions={[]}
            outputColumns={outputColumns}
            leftLabel="左侧"
            rightLabel="右侧"
            onProgrammaticChange={(columns) => {
              form.setFieldValue('outputColumns', columns);
              markDirty({ ...form.getFieldsValue(true), outputColumns: columns });
            }}
          />
          <Button
            size="small"
            disabled={!leftTable || !rightTable}
            onClick={() => {
              if (!leftTable || !rightTable) return;
              Modal.confirm({ title: '按当前叠加方式重建建议？',
                content: '将覆盖当前字段改名、排除和排序；擦除模式的新建议会排除右侧字段。', okText: '重建',
                onOk: () => {
                  const columns = projectForOperation(suggestJoinOutputColumns(leftTable, rightTable), operation);
                  form.setFieldValue('outputColumns', columns);
                  markDirty();
                },
              });
            }}
          >按当前叠加方式重建建议</Button>
        </Modal>
      </Form>
    </Space>
  );
};

export default SpatialOverlayInspector;
