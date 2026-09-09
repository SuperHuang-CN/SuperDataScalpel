import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { SettingOutlined } from '@ant-design/icons';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type SpatialNearestConfiguration,
  type SpatialNearestConnectionLines,
  type SpatialNearestMatching,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { JoinOutputColumnsEditor } from '../../components/JoinOutputColumnsEditor';
import { suggestJoinOutputColumns } from '../../components/joinOutputColumns';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import {
  spatialColumnOptions,
  spatialGeometryColumns,
  spatialTableOptions,
} from '../spatialInspectorOptions';
import { createNearestMatching, usesExactNearest } from './matching';
import { ConnectionLinesModal } from './ConnectionLinesModal';

import { spatialDistanceUnitOptions as unitOptions, spatialUnitHelp } from '../spatialUnits';

const fingerprint = (value: SpatialNearestConfiguration) => JSON.stringify(value);

const SpatialNearestInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialNearest>) => {
  const [form] = Form.useForm<SpatialNearestConfiguration>();
  const [projectionOpen, setProjectionOpen] = useState(false);
  const [lineDraft, setLineDraft] = useState<SpatialNearestConnectionLines | null>(null);
  const matching: SpatialNearestMatching | null | undefined = Form.useWatch('matching', { form, preserve: true });
  const exact = usesExactNearest({ matching });
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const candidateTableName = Form.useWatch('candidateTableName', form) ?? '';
  const sourceGeometryColumnName = Form.useWatch('sourceGeometryColumnName', form) ?? '';
  const candidateGeometryColumnName = Form.useWatch('candidateGeometryColumnName', form) ?? '';
  const candidateIdColumnName = Form.useWatch('candidateIdColumnName', form) ?? '';
  const distanceMethod = Form.useWatch('distanceMethod', form);
  const maximumDistance = Form.useWatch('maximumDistance', form);
  const maximumDistanceUnit = Form.useWatch('maximumDistanceUnit', form);
  const distanceOutputUnit = Form.useWatch('distanceOutputUnit', form);
  const outputColumns = Form.useWatch('outputColumns', { form, preserve: true }) ?? [];
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const candidateTable = tables.find((table) => table.name === candidateTableName);
  const sourceGeometry = spatialGeometryColumns(sourceTable)
    .find((column) => column.name === sourceGeometryColumnName);
  const candidateGeometry = spatialGeometryColumns(candidateTable)
    .find((column) => column.name === candidateGeometryColumnName);
  const isWgs84 = sourceGeometry?.geometry?.crs.authority === 'EPSG'
    && sourceGeometry.geometry.crs.code === 4326;
  const geometryMismatch = Boolean(
    sourceGeometry?.geometry
    && candidateGeometry?.geometry
    && (sourceGeometry.geometry.crs.authority !== candidateGeometry.geometry.crs.authority
      || sourceGeometry.geometry.crs.code !== candidateGeometry.geometry.crs.code
      || sourceGeometry.geometry.dimension !== candidateGeometry.geometry.dimension),
  );

  const toConfiguration = (
    values: SpatialNearestConfiguration,
  ): SpatialNearestConfiguration => ({
    ...values,
    sourceTableName: values.sourceTableName ?? '',
    sourceGeometryColumnName: values.sourceGeometryColumnName ?? '',
    candidateTableName: values.candidateTableName ?? '',
    candidateGeometryColumnName: values.candidateGeometryColumnName ?? '',
    candidateIdColumnName: values.candidateIdColumnName ?? '',
    distanceMethod: values.distanceMethod ?? null,
    nearestCount: values.nearestCount ?? 0,
    maximumDistance: values.maximumDistance ?? null,
    maximumDistanceUnit: values.maximumDistanceUnit ?? null,
    includeUnmatched: values.includeUnmatched ?? false,
    outputTableName: values.outputTableName?.trim() ?? '',
    distanceColumnName: values.distanceColumnName?.trim() ?? '',
    distanceOutputUnit: values.distanceOutputUnit ?? 'SOURCE_CRS_UNIT',
    rankColumnName: values.rankColumnName == null ? null : values.rankColumnName.trim(),
    outputColumns: (values.outputColumns ?? []).map((item) => ({
      ...item,
      outputColumnName: item.outputColumnName.trim(),
    })),
  });

  const markDirty = (values = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(toConfiguration(values)) !== fingerprint(node.configuration));
  };
  const submit = (values: SpatialNearestConfiguration) => {
    onApply({ id: node.id, type: node.type, configuration: toConfiguration(values) });
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

  const suggestProjectionIfEmpty = (values: SpatialNearestConfiguration) => {
    if ((values.outputColumns ?? []).length > 0) return;
    const left = tables.find((table) => table.name === values.sourceTableName);
    const right = tables.find((table) => table.name === values.candidateTableName);
    if (left && right && left.name !== right.name) {
      form.setFieldValue('outputColumns', suggestJoinOutputColumns(left, right));
    }
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Form<SpatialNearestConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={() => submit(form.getFieldsValue(true))}
        onValuesChange={() => {
          suggestProjectionIfEmpty(form.getFieldsValue(true));
          markDirty();
        }}
      >
        <Form.Item label={<span className="canvas-inspector-field-label">匹配策略
          <ContextHelp ariaLabel="最近邻匹配策略说明" content="真实距离：按来源唯一 ID 区分行，恢复同距候选后按距离和候选 ID 排名。旧版保持原 KNN 行为，非点测地使用质心，平局不保证稳定；切换会影响结果。" />
        </span>}>
          <Select aria-label="匹配策略" value={exact ? 'EXACT_DISTANCE' : 'LEGACY_KNN'}
            options={[{ value: 'EXACT_DISTANCE', label: '真实距离 · 稳定同距排序' }, { value: 'LEGACY_KNN', label: '旧版 KNN' }]}
            onChange={semantics => Modal.confirm({ title: '切换最近邻匹配策略？',
              content: '真实距离策略要求两侧 ID 非空且唯一，测地线暂仅支持 Point。旧版保留原 KNN / 质心行为且忽略连接线配置。已有字段和连接线草稿不会清除，请检查下游结果。',
              okText: '确认切换', cancelText: '取消', onOk: () => {
                form.setFieldValue('matching', { ...(form.getFieldValue('matching') ?? createNearestMatching()), semantics });
                markDirty();
              } })} />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="sourceTableName" label="来源表" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!validation}
              options={spatialTableOptions(tables, sourceTableName)}
              placeholder="选择来源表"
            />
          </Form.Item>
          <Form.Item name="candidateTableName" label="候选表" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!validation}
              options={spatialTableOptions(tables, candidateTableName)}
              placeholder="选择候选表"
            />
          </Form.Item>
          <Form.Item name="sourceGeometryColumnName" label="来源 Geometry" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!sourceTable}
              options={spatialColumnOptions(
                sourceTable?.columns ?? [], sourceGeometryColumnName,
                (column) => column.fieldType === 'GEOMETRY',
              )}
            />
          </Form.Item>
          <Form.Item name="candidateGeometryColumnName" label="候选 Geometry" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={!candidateTable}
              options={spatialColumnOptions(
                candidateTable?.columns ?? [], candidateGeometryColumnName,
                (column) => column.fieldType === 'GEOMETRY',
              )}
            />
          </Form.Item>
        </div>
        {geometryMismatch && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            两侧 CRS 或坐标维度不一致，请先使用空间转换节点。
          </Typography.Text>
        )}
        {exact && <Form.Item name={['matching', 'sourceIdColumnName']} label="来源唯一字段" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" disabled={!sourceTable}
            options={spatialColumnOptions(sourceTable?.columns ?? [], matching?.sourceIdColumnName ?? '', column => column.fieldType !== 'GEOMETRY')} />
        </Form.Item>}
        <Form.Item name="candidateIdColumnName" label={<span className="canvas-inspector-field-label">候选唯一字段
          <ContextHelp ariaLabel="最近邻唯一字段说明" content={exact ? '两侧身份字段在实际读取时验证非空和唯一，重复或 NULL 导致执行失败；编译不扫描真实数据。候选 ID 用于同距排序。' : '旧版只将候选 ID 用于 KNN 候选内排序，不保证所有同距候选参与排序。'} />
        </span>} rules={[{ required: true }]}>
          <Select
            showSearch
            optionFilterProp="label"
            disabled={!candidateTable}
            options={spatialColumnOptions(
              candidateTable?.columns ?? [],
              candidateIdColumnName,
              (column) => column.fieldType !== 'GEOMETRY',
            )}
          />
        </Form.Item>
        <Form.Item
          name="distanceMethod"
          label={(
            <span className="canvas-inspector-field-label">
              距离方法
              <ContextHelp
                ariaLabel="距离方法说明"
                content="平面距离测量几何最近位置，是平台扩展；真实测地距离暂仅支持 EPSG:4326 XY Point，不用非点质心冒充最近位置。"
              />
            </span>
          )}
          rules={[{ required: true }]}
        >
          <Select placeholder="明确选择距离方法" options={[
            { value: 'PLANAR', label: '平面（扩展）' },
            { value: 'GEODESIC', label: exact ? '测地线（点）' : '测地线（旧版质心）' },
          ]} />
        </Form.Item>
        {distanceMethod === 'GEODESIC' && sourceGeometry && !isWgs84 && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            测地线距离只支持 EPSG:4326 XY。
          </Typography.Text>
        )}
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="nearestCount" label="最近数量" rules={[{ required: true }]}>
            <InputNumber min={1} max={100} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={<span className="canvas-inspector-field-label">最大距离（可选）
            <ContextHelp ariaLabel="最近邻搜索范围说明" content="留空会全范围搜索。真实距离策略用 KNN 上界恢复半径内全部候选，大量同距要素可能产生很大的中间结果，建议配置业务半径。" />
          </span>}>
            <Space.Compact block>
              <Form.Item name="maximumDistance" noStyle>
                <InputNumber min={Number.MIN_VALUE} style={{ width: '52%' }} />
              </Form.Item>
              <Form.Item name="maximumDistanceUnit" noStyle>
                <Select allowClear options={unitOptions} style={{ width: '48%' }} />
              </Form.Item>
            </Space.Compact>
            {maximumDistance == null && maximumDistanceUnit != null && (
              <Typography.Text type="danger" className="canvas-field-inline-warning">
                未设置距离时请清空单位。
              </Typography.Text>
            )}
          </Form.Item>
        </div>
        <Form.Item name="includeUnmatched" label="保留未命中来源" valuePropName="checked">
          <Switch />
        </Form.Item>
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="distanceColumnName" label="距离字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="distanceOutputUnit" label={<Space>距离字段单位<ContextHelp ariaLabel="距离单位说明" content={spatialUnitHelp} /></Space>} rules={[{ required: true }]}>
            <Select options={unitOptions} />
          </Form.Item>
          <Form.Item name="rankColumnName" label="排名字段（可选）">
            <Input allowClear />
          </Form.Item>
          <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
        </div>
        {distanceMethod === 'GEODESIC' && distanceOutputUnit === 'SOURCE_CRS_UNIT' && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            测地线结果不能使用来源 CRS 单位，请选择明确的线性距离单位。
          </Typography.Text>
        )}
        {exact && distanceMethod === 'GEODESIC' && [sourceGeometry, candidateGeometry].some(column =>
          column?.geometry != null && !['POINT', 'GEOMETRY'].includes(column.geometry.kind)) && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">真实测地最近位置暂仅支持 Point，线面能力尚未实现。</Typography.Text>
        )}
        {exact && <div className="canvas-processor-section-header">
          <Space size={6}><Typography.Text strong>连接线结果</Typography.Text><Tag>{matching?.connectionLines?.enabled ? '已启用' : '关闭'}</Tag></Space>
          <Button size="small" aria-label="设置连接线" icon={<SettingOutlined />} onClick={() => setLineDraft({
            ...(matching?.connectionLines ?? { enabled: false, outputTableName: '', geometryColumnName: 'connection', maximumGeodesicSegmentLength: 10, maximumGeodesicSegmentLengthUnit: 'KILOMETERS' }),
          })}>设置连接线</Button>
        </div>}
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>输出字段</Typography.Text>
            <Tag>{outputColumns.filter((item) => item.included).length} / {outputColumns.length}</Tag>
          </Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setProjectionOpen(true)}>
            设置
          </Button>
        </div>
        <Modal
          open={projectionOpen}
          width={860}
          title="设置最近邻输出字段"
          okText="完成"
          cancelText="关闭"
          onOk={() => setProjectionOpen(false)}
          onCancel={() => setProjectionOpen(false)}
        >
          <JoinOutputColumnsEditor
            left={sourceTable}
            right={candidateTable}
            conditions={[]}
            outputColumns={outputColumns}
            leftLabel="来源"
            rightLabel="候选"
            onProgrammaticChange={(columns) => {
              form.setFieldValue('outputColumns', columns);
              markDirty({ ...form.getFieldsValue(true), outputColumns: columns });
            }}
          />
        </Modal>
      </Form>
      {lineDraft && <ConnectionLinesModal value={lineDraft} geodesic={distanceMethod === 'GEODESIC'} onCancel={() => setLineDraft(null)} onSave={value => {
        form.setFieldValue(['matching', 'connectionLines'], value);
        markDirty();
        setLineDraft(null);
      }} />}
    </Space>
  );
};

export default SpatialNearestInspector;
