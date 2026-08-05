import { Form, Input, Select } from 'antd';
import { useImperativeHandle, useMemo, useState } from 'react';
import { buildDataSourceSearch, useDataSources, useSpatialFeatureResources } from '../../../../datasource';
import { CanvasNodeType, type CanvasNodeConfigurationUpdateByType } from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

interface Values { dataSourceId: string; resourceId: string; outputTableName: string; }

const Inspector = ({ node, onApply, onDirtyChange, inspectorRef }: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialServiceInput>) => {
  const [form] = Form.useForm<Values>();
  const sourceId = Form.useWatch('dataSourceId', form) ?? '';
  const [sourceOpen, setSourceOpen] = useState(false);
  const sourcesQuery = useDataSources(useMemo(() => ({ search: buildDataSourceSearch({ purpose: 'SOURCE', enabled: true }), page: 0, size: 100, sort: 'name' }), []), sourceOpen || Boolean(sourceId));
  const resourcesQuery = useSpatialFeatureResources(sourceId || undefined, Boolean(sourceId));
  const sources = (sourcesQuery.data?.content ?? []).filter((source) => source.type === 'ARCGIS_REST' || source.type === 'WFS');
  const apply = (values: Values) => {
    onApply({ id: node.id, type: node.type, configuration: { dataSourceId: values.dataSourceId ?? '', resourceId: values.resourceId ?? '', outputTableName: values.outputTableName?.trim() ?? '' } } as CanvasNodeConfigurationUpdateByType<typeof CanvasNodeType.SpatialServiceInput>);
    onDirtyChange(false);
  };
  useImperativeHandle(inspectorRef, (): CanvasNodeInspectorHandle => ({ apply: async () => { try { apply(await form.validateFields()); return true; } catch { return false; } } }));
  return <Form<Values> autoComplete="off" form={form} layout="vertical" initialValues={node.configuration} onFinish={apply} onValuesChange={() => onDirtyChange(true)}>
    <Form.Item name="dataSourceId" label="空间服务数据源" rules={[{ required: true, message: '请选择 ArcGIS REST 或 WFS 数据源' }]}><Select showSearch open={sourceOpen} onOpenChange={setSourceOpen} optionFilterProp="label" loading={sourcesQuery.isFetching} options={sources.map((source) => ({ value: source.id, label: `${source.name} · ${source.type}` }))} /></Form.Item>
    <Form.Item name="resourceId" label="空间要素资源" rules={[{ required: true, message: '请选择已登记的空间要素资源' }]}><Select showSearch disabled={!sourceId} loading={resourcesQuery.isFetching} options={(resourcesQuery.data ?? []).map((resource) => ({ value: resource.id, label: `${resource.name} · ${resource.epsgCode ? `EPSG:${resource.epsgCode}` : '未指定坐标系'}`, disabled: !resource.enabled }))} /></Form.Item>
    <Form.Item name="outputTableName" label="输出表名" rules={[{ required: true, whitespace: true }, { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' }]}><Input placeholder="例如 spatial_features" /></Form.Item>
  </Form>;
};

export default Inspector;
