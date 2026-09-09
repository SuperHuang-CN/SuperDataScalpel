import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { Button, Form, Input, InputNumber, Modal, Segmented, Select, Space, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type SpatialPointClusterConfiguration,
  type SpatialPointClusterParameters,
  type SpatialDbscanOptions,
  type SpatialHdbscanOptions,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';
import { spatialColumnOptions, spatialGeometryColumns, spatialTableOptions } from '../spatialInspectorOptions';
import { trackDistanceUnitOptions } from '../trackOptions';
import { spatialUnitHelp } from '../spatialUnits';
import { spatialDurationUnitOptions } from '../spatialAggregationOptions';
import { createDbscanOptions, dbscanHelp } from './dbscanOptions';
import { createHdbscanOptions, hdbscanFields, hdbscanFieldErrors, hdbscanHelp } from './hdbscanOptions';

const fingerprint = (value: SpatialPointClusterConfiguration) => JSON.stringify(value);

const SpatialPointClusterInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialPointCluster>) => {
  const [form] = Form.useForm<SpatialPointClusterConfiguration>();
  const [pendingMode, setPendingMode] = useState<NonNullable<SpatialDbscanOptions['mode']> | null>(null);
  const [pendingAlgorithm, setPendingAlgorithm] = useState<SpatialPointClusterParameters['algorithm'] | null>(null);
  const [diagnosticDraft, setDiagnosticDraft] = useState<SpatialHdbscanOptions | null>(null);
  const [algorithmDrafts, setAlgorithmDrafts] = useState<Partial<Record<SpatialPointClusterParameters['algorithm'], SpatialPointClusterParameters>>>(
    () => ({ [node.configuration.parameters.algorithm]: node.configuration.parameters }),
  );
  const current = Form.useWatch<SpatialPointClusterConfiguration>([], { form, preserve: true });
  const dbscan = current ? current.dbscan : node.configuration.dbscan;
  const hdbscan = current ? current.hdbscan : node.configuration.hdbscan;
  const sourceTableName = Form.useWatch('sourceTableName', form) ?? '';
  const pointGeometryColumnName = Form.useWatch('pointGeometryColumnName', form) ?? '';
  const featureIdColumnName = Form.useWatch('featureIdColumnName', form) ?? '';
  const parameters = current?.parameters ?? node.configuration.parameters;
  const distanceMethod = Form.useWatch('distanceMethod', form) ?? 'PLANAR';
  const tables = validation?.inputTables ?? [];
  const sourceTable = tables.find((table) => table.name === sourceTableName);
  const columns = sourceTable?.columns ?? [];
  const reserved = [...columns.map(column => column.name), current?.clusterIdColumnName ?? node.configuration.clusterIdColumnName,
    current?.noiseColumnName ?? node.configuration.noiseColumnName];
  const diagnosticErrors = diagnosticDraft ? hdbscanFieldErrors(diagnosticDraft, reserved) : {};
  const savedDiagnosticIssues = hdbscan ? Object.keys(hdbscanFieldErrors(hdbscan, reserved)).length : 4;
  const pointGeometry = spatialGeometryColumns(sourceTable)
    .find((column) => column.name === pointGeometryColumnName);

  const normalize = (
    value: SpatialPointClusterConfiguration,
  ): SpatialPointClusterConfiguration => ({
    sourceTableName: value.sourceTableName ?? '',
    pointGeometryColumnName: value.pointGeometryColumnName ?? '',
    featureIdColumnName: value.featureIdColumnName ?? '',
    distanceMethod: value.distanceMethod ?? null,
    parameters: value.parameters ?? node.configuration.parameters,
    outputTableName: value.outputTableName?.trim() ?? '',
    clusterIdColumnName: value.clusterIdColumnName?.trim() ?? '',
    noiseColumnName: value.noiseColumnName?.trim() ?? '',
    ...('dbscan' in value ? { dbscan: value.dbscan } : {}),
    ...('hdbscan' in value ? { hdbscan: value.hdbscan } : {}),
  });
  const markDirty = (value = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(normalize(value)) !== fingerprint(node.configuration));
  };
  const submit = (value: SpatialPointClusterConfiguration) => {
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
  const updateParameters = (value: SpatialPointClusterParameters) => {
    setAlgorithmDrafts((drafts) => ({ ...drafts, [value.algorithm]: value }));
    form.setFieldValue('parameters', value);
    markDirty({ ...form.getFieldsValue(true), parameters: value });
  };
  const updateDbscan = (value: SpatialDbscanOptions) => {
    form.setFieldValue('dbscan', value);
    const values: SpatialPointClusterConfiguration = form.getFieldsValue(true);
    markDirty({ ...values, dbscan: value });
  };

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
      <Form<SpatialPointClusterConfiguration>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={node.configuration}
        onFinish={submit}
        onValuesChange={() => markDirty()}
      >
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="sourceTableName" label="来源点表" rules={[{ required: true }]}>
            <Select
              showSearch optionFilterProp="label" disabled={!validation}
              options={spatialTableOptions(tables, sourceTableName)}
            />
          </Form.Item>
          <Form.Item name="pointGeometryColumnName" label="Point Geometry" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
              columns,
              pointGeometryColumnName,
              (column) => column.fieldType === 'GEOMETRY' && column.geometry?.kind === 'POINT',
            )} />
          </Form.Item>
        </div>
        <Form.Item name="featureIdColumnName" label="要素唯一字段" rules={[{ required: true }]}>
          <Select showSearch optionFilterProp="label" options={spatialColumnOptions(
            columns,
            featureIdColumnName,
            (column) => column.fieldType !== 'GEOMETRY',
          )} />
        </Form.Item>
        <Form.Item name="distanceMethod" label="距离方法" rules={[{ required: true }]}>
          <Segmented block options={[
            { value: 'PLANAR', label: '平面' },
            { value: 'GEODESIC', label: '测地线' },
          ]} />
        </Form.Item>
        {distanceMethod === 'GEODESIC'
          && (pointGeometry?.geometry?.crs.authority !== 'EPSG' || pointGeometry.geometry.crs.code !== 4326) && (
          <Typography.Text type="warning" className="canvas-field-inline-warning">
            测地线聚类只支持 EPSG:4326 XY 点。
          </Typography.Text>
        )}
        <Form.Item label={<span className="canvas-inspector-field-label">聚类算法<ContextHelp
          ariaLabel="点聚类算法说明"
          content="DBSCAN 支持空间与 Linear 时空密度连通；HDBSCAN 从密度层次提取点簇并输出四类诊断。Multi-scale 只是旧协议占位，不属于 GA Find Point Clusters，不会自动降级执行其他算法。"
        /></span>}>
          <Segmented<SpatialPointClusterParameters['algorithm']>
            block
            aria-label="聚类算法"
            value={parameters.algorithm}
            options={[
              { value: 'DBSCAN', label: 'DBSCAN' },
              { value: 'HDBSCAN', label: 'HDBSCAN' },
              ...(algorithmDrafts.MULTI_SCALE ? [{ value: 'MULTI_SCALE' as const, label: '旧多尺度（不支持）' }] : []),
            ]}
            onChange={setPendingAlgorithm}
          />
        </Form.Item>
        {parameters.algorithm === 'MULTI_SCALE' && (
          <Typography.Text type="danger" className="canvas-field-inline-warning">
            当前算法暂不可运行。切换需确认；本面板内可恢复各算法草稿，应用仅保存当前算法，关闭后不保留其他分支。
          </Typography.Text>
        )}
        {parameters.algorithm === 'DBSCAN' && (
          <div className="canvas-spatial-pair-grid">
            <Form.Item label={<Space>搜索距离<ContextHelp ariaLabel="聚类距离单位说明" content={spatialUnitHelp} /></Space>} required>
              <Space.Compact block>
                <InputNumber
                  aria-label="聚类搜索距离"
                  min={Number.MIN_VALUE}
                  value={parameters.searchDistance}
                  onChange={(searchDistance) => updateParameters({
                    ...parameters, searchDistance: searchDistance ?? 0,
                  })}
                  style={{ width: '55%' }}
                />
                <Select
                  value={parameters.searchDistanceUnit}
                  options={trackDistanceUnitOptions}
                  onChange={(searchDistanceUnit) => updateParameters({
                    ...parameters, searchDistanceUnit,
                  })}
                  style={{ width: '45%' }}
                />
              </Space.Compact>
            </Form.Item>
            <Form.Item label={<Space>最少要素<ContextHelp ariaLabel="DBSCAN 密度门槛说明" content="包括自身在内的邻居数达到该值才成为核心点。这不是最终每个簇的硬性最小行数；边界点不形成核心连通桥。" /></Space>} required>
              <InputNumber
                aria-label="DBSCAN 最少要素"
                min={2} max={100000} precision={0} value={parameters.minimumFeatures}
                onChange={(minimumFeatures) => updateParameters({
                  ...parameters, minimumFeatures: minimumFeatures ?? 0,
                })}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </div>
        )}
        {parameters.algorithm === 'DBSCAN' && <>
          <Form.Item label={<Space>聚类语义<ContextHelp ariaLabel="DBSCAN 时空与执行说明" content={dbscanHelp} /></Space>}>
            <Select aria-label="DBSCAN 聚类语义" value={dbscan ? dbscan.mode : 'LEGACY_SPATIAL'}
              status={dbscan && !dbscan.mode ? 'error' : undefined}
              options={[{ value: 'SPATIAL', label: '空间密度连通' }, { value: 'LINEAR', label: 'Linear 时空密度连通' },
                { value: 'LEGACY_SPATIAL', label: '旧空间算法' }]}
              onChange={setPendingMode} />
          </Form.Item>
          {dbscan?.mode === 'LINEAR' && <div className="canvas-spatial-pair-grid">
            <Form.Item label="时间字段" required>
              <Select aria-label="聚类时间字段" showSearch optionFilterProp="label" value={dbscan.timeColumnName}
                status={!dbscan.timeColumnName || sourceTable && !columns.some(c => c.name === dbscan.timeColumnName && c.fieldType === 'TIMESTAMP') ? 'error' : undefined}
                options={spatialColumnOptions(columns, dbscan.timeColumnName, c => c.fieldType === 'TIMESTAMP')}
                onChange={(timeColumnName) => updateDbscan({ ...dbscan, timeColumnName })} />
            </Form.Item>
            <Form.Item label="时间邻域" required>
              <Space.Compact block>
                <InputNumber aria-label="聚类时间邻域" min={1} precision={0} value={dbscan.searchDuration}
                  status={dbscan.searchDuration == null || dbscan.searchDuration <= 0 ? 'error' : undefined}
                  onChange={(searchDuration) => updateDbscan({ ...dbscan, searchDuration })} />
                <Select aria-label="聚类时间单位" value={dbscan.searchDurationUnit} options={spatialDurationUnitOptions}
                  status={!dbscan.searchDurationUnit ? 'error' : undefined}
                  onChange={(searchDurationUnit) => updateDbscan({ ...dbscan, searchDurationUnit })} />
              </Space.Compact>
            </Form.Item>
          </div>}
        </>}
        {parameters.algorithm !== 'DBSCAN' && (
          <Form.Item label={<Space>最少要素{parameters.algorithm === 'HDBSCAN' && <ContextHelp ariaLabel="HDBSCAN 密度与规模说明" content={hdbscanHelp} />}</Space>} required>
            <InputNumber
              aria-label="HDBSCAN 最少要素"
              min={2} max={100000} precision={0} value={parameters.minimumFeatures}
              onChange={(minimumFeatures) => updateParameters({
                ...parameters, minimumFeatures: minimumFeatures ?? 0,
              })}
              style={{ width: '100%' }}
            />
          </Form.Item>
        )}
        {parameters.algorithm === 'HDBSCAN' && <Form.Item label="诊断输出">
          <Space size={6} wrap>
            <Typography.Text type={savedDiagnosticIssues ? 'danger' : undefined}>
              {hdbscan ? savedDiagnosticIssues ? `${savedDiagnosticIssues} 个字段问题` : '已配置 4 项诊断' : '待配置 4 项诊断'}
            </Typography.Text>
            <Button size="small" aria-label="配置 HDBSCAN 诊断字段" onClick={() => setDiagnosticDraft({ ...(hdbscan ?? createHdbscanOptions()) })}>设置</Button>
            <ContextHelp ariaLabel="HDBSCAN 诊断说明" content={hdbscanHelp} />
          </Space>
        </Form.Item>}
        <div className="canvas-spatial-pair-grid">
          <Form.Item name="clusterIdColumnName" label="簇 ID 字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="noiseColumnName" label="噪声标记字段" rules={[{ required: true, whitespace: true }]}>
            <Input />
          </Form.Item>
        </div>
        <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }]}>
          <Input placeholder="例如 device_clusters" />
        </Form.Item>
      </Form>
      <Modal width={680} open={diagnosticDraft !== null} title="HDBSCAN 诊断输出字段" okText="保存诊断草稿" cancelText="取消"
        onCancel={() => setDiagnosticDraft(null)} onOk={() => {
          if (diagnosticDraft) {
            form.setFieldValue('hdbscan', diagnosticDraft);
            markDirty({ ...form.getFieldsValue(true), hdbscan: diagnosticDraft });
          }
          setDiagnosticDraft(null);
        }}>
        {diagnosticDraft && <Form layout="vertical" autoComplete="off">
          {hdbscanFields.map(({ key, label, help }) => <Form.Item key={key}
            label={<Space>{label}<ContextHelp ariaLabel={`${label}含义`} content={help} /></Space>}
            validateStatus={diagnosticErrors[key] ? 'error' : undefined} help={diagnosticErrors[key]}>
            <Input aria-label={`${label}输出字段`} value={diagnosticDraft[key]}
              onChange={event => setDiagnosticDraft({ ...diagnosticDraft, [key]: event.target.value })} />
          </Form.Item>)}
        </Form>}
      </Modal>
      <Modal open={pendingMode !== null} title="切换聚类语义？" okText="确认切换" cancelText="取消"
        onCancel={() => setPendingMode(null)} onOk={() => {
          if (pendingMode) updateDbscan({ ...createDbscanOptions(), ...dbscan, mode: pendingMode });
          setPendingMode(null);
        }}>
        切换可能改变簇成员与噪声。空间半径和隐藏的时间配置保持不变；旧算法与显式密度连通分别执行。
      </Modal>
      <Modal open={pendingAlgorithm !== null} title="切换聚类算法？" okText="确认切换算法" cancelText="取消"
        onCancel={() => setPendingAlgorithm(null)} onOk={() => {
          if (pendingAlgorithm) {
            const next = algorithmDrafts[pendingAlgorithm] ?? (pendingAlgorithm === 'DBSCAN' ? {
              algorithm: 'DBSCAN' as const, searchDistance: 200,
              searchDistanceUnit: 'METERS' as const, minimumFeatures: parameters.minimumFeatures || 5,
            } : pendingAlgorithm === 'HDBSCAN' ? { algorithm: 'HDBSCAN' as const, minimumFeatures: parameters.minimumFeatures || 5 } : null);
            if (next) {
              if (next.algorithm === 'HDBSCAN' && !hdbscan) form.setFieldValue('hdbscan', createHdbscanOptions());
              updateParameters(next);
            }
          }
          setPendingAlgorithm(null);
        }}>
        算法改变会影响聚类结果。各分支参数仅在当前面板内保留供恢复，应用只保存当前算法；隐藏的时空设置保持不变。
        {pendingAlgorithm === 'HDBSCAN' && 'HDBSCAN 不使用空间半径或时间邻域，当前实现尚未完成规模及 ArcGIS 数值对照。'}
        {pendingAlgorithm === 'MULTI_SCALE' && '恢复未实现算法只能保存草稿，不能运行。'}
      </Modal>
    </Space>
  );
};

export default SpatialPointClusterInspector;
