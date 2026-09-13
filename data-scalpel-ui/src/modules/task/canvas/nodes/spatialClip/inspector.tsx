import {
  CompactAlert as Alert,
  ContextHelp,
} from '../../../../../shared/components/ContextualFeedback';
import { Form, Input, Select, Space, Tag, Typography } from 'antd';
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
  geometryPolicy: values.geometryPolicy ?? null,
  maskCombination: values.maskCombination ?? null,
});

const sourceFamily = (kind?: string) => {
  if (kind === 'POINT' || kind === 'MULTIPOINT') return 'MultiPoint';
  if (kind === 'LINESTRING' || kind === 'MULTILINESTRING') return 'MultiLineString';
  if (kind === 'POLYGON' || kind === 'MULTIPOLYGON') return 'MultiPolygon';
  return null;
};

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
  const geometryPolicy = Form.useWatch('geometryPolicy', { form, preserve: true }) ?? null;
  const maskCombination = Form.useWatch('maskCombination', { form, preserve: true }) ?? null;
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
  const outputFamily = sourceFamily(sourceGeometry?.geometry?.kind);
  const sourceFamilyInvalid = geometryPolicy === 'SOURCE_FAMILY_2D'
    && Boolean(sourceGeometry)
    && outputFamily == null;

  const submit = (values: SpatialClipConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: normalize(values) });
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
        title="INNER 裁剪"
        description={maskCombination === 'PAIRWISE' || maskCombination == null
          ? '逐条 Mask 裁剪：一个来源命中多个 Mask 时会输出多行；Mask 属性不会进入结果。'
          : '整体覆盖裁剪：每条来源先合并全部相交 Mask，再输出一条裁剪结果；Mask 属性不会进入结果。'}
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
          validateStatus={sourceGeometryMissing || sourceFamilyInvalid ? 'error' : undefined}
          help={sourceGeometryMissing
            ? '原字段已失效，配置仍被保留。'
            : sourceFamilyInvalid
              ? '保持来源家族只接受明确的点、线、面类型；请先明确 Geometry 类型。'
              : undefined}
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

        <Form.Item
          label={<span className="canvas-inspector-field-label">多 Mask 处理<ContextHelp
            ariaLabel="多 Mask 裁剪说明"
            content={<>
              <p>合并后裁剪：对每条来源要素，只合并与它相交的 Mask，再执行一次裁剪。重叠 Mask 不会重复输出覆盖区域，分离片段保留在同一个 Multi Geometry 中。</p>
              <p>逐条裁剪：保留旧行为，每个来源与每条相交 Mask 分别输出一行，重叠 Mask 可能导致来源属性和覆盖区域重复。</p>
              <p>两种方式都不输出 Mask 属性，也不增加 ArcGIS 容差、吸附或自动修复。</p>
            </>}
          /></span>}
        >
          <Select
            aria-label="多 Mask 处理方式"
            value={maskCombination ?? 'PAIRWISE'}
            options={[
              { value: 'DISSOLVE_ALL', label: '合并相交 Mask 后裁剪（推荐）' },
              { value: 'PAIRWISE', label: '每条 Mask 分别裁剪（旧版）' },
            ]}
            onChange={(value) => {
              form.setFieldValue('maskCombination', value);
              onDirtyChange(fingerprint(normalize({
                ...form.getFieldsValue(true),
                maskCombination: value,
              })) !== fingerprint(node.configuration));
            }}
          />
        </Form.Item>

        <Typography.Text strong>输出</Typography.Text>
        <Form.Item
          label={<span className="canvas-inspector-field-label">结果 Geometry<ContextHelp
            ariaLabel="空间裁剪结果 Geometry 说明"
            content={<>
              <p>保持来源家族：点、线、面分别输出二维 MultiPoint、MultiLineString、MultiPolygon；仅边界接触形成的低维片段会被过滤。</p>
              <p>旧版通用 Geometry：保留历史行为，相交结果可能是任意点、线或面家族。</p>
              <p>多条 Mask 是否合并由“多 Mask 处理”单独控制。两种 Geometry 模式都不提供 ArcGIS 容差、吸附或自动修复。</p>
            </>}
          /></span>}
        >
          <Select
            aria-label="空间裁剪结果 Geometry"
            value={geometryPolicy ?? 'LEGACY_ANY_DIMENSION'}
            options={[
              { value: 'SOURCE_FAMILY_2D', label: '保持来源家族 · 二维多部件' },
              { value: 'LEGACY_ANY_DIMENSION', label: '旧版 · 通用 Geometry' },
            ]}
            onChange={(value) => {
              form.setFieldValue('geometryPolicy', value);
              onDirtyChange(fingerprint(normalize({
                ...form.getFieldsValue(true),
                geometryPolicy: value,
              })) !== fingerprint(node.configuration));
            }}
          />
        </Form.Item>
        <Typography.Text type={sourceFamilyInvalid ? 'danger' : 'secondary'}>
          {geometryPolicy === 'SOURCE_FAMILY_2D'
            ? outputFamily ? `输出 ${outputFamily} · XY` : '请选择明确的点、线或面来源 Geometry'
            : '输出通用 GEOMETRY，保留历史相交结果家族'}
        </Typography.Text>
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
      </Form>
    </Space>
  );
};

export default SpatialClipInspector;
