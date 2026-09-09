import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { Form, Input, Space, Switch, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useDataModelSpatialPreview } from '../../../model';
import { useSpatialDataServiceModelCandidates } from '../../hooks/useDataServices';
import type { SpatialDataServiceModelCandidate } from '../../model/dataService';
import type { DataServiceFormValues } from '../../model/dataServiceEditor';

interface SpatialServiceEditorProps {
  form: ReturnType<typeof Form.useForm<DataServiceFormValues>>[0];
  serviceId: string;
  readOnly: boolean;
  canViewModels: boolean;
}

export const SpatialServiceEditor = ({ form, serviceId, readOnly, canViewModels }: SpatialServiceEditorProps) => {
  const [keyword, setKeyword] = useState('');
  const [includeUnavailable, setIncludeUnavailable] = useState(false);
  const selectedModelId = Form.useWatch('modelId', form);
  const request = useMemo(() => ({
    search: keyword.trim()
      ? `(name:*"${keyword.trim().replaceAll('"', '\\"')}"* OR code:*"${keyword.trim().replaceAll('"', '\\"')}"*)`
      : undefined,
    page: 0,
    size: 100,
    sort: 'name,code',
  }), [keyword]);
  const candidatesQuery = useSpatialDataServiceModelCandidates(
    serviceId, request, includeUnavailable, canViewModels,
  );
  const previewQuery = useDataModelSpatialPreview(selectedModelId, canViewModels && Boolean(selectedModelId));
  const selected = candidatesQuery.data?.content.find((candidate) => candidate.id === selectedModelId);
  const geometryPreview = previewQuery.data?.geometryFields.find((field) => field.code === selected?.geometryColumn);

  return (
    <div className="data-service-standard-definition-editor spatial-service-definition-editor data-service-management-scope">
      <Form.Item<DataServiceFormValues> name="modelId" hidden rules={[
        { required: true, message: '请选择空间模型' },
        {
          validator: async () => {
            if (!selectedModelId) return;
            if (previewQuery.isFetching) throw new Error('正在检查物理表与 PostGIS 能力，请稍后保存');
            if (previewQuery.isError) throw new Error('空间模型物理检查失败');
            if (previewQuery.data && !previewQuery.data.supported) {
              throw new Error(previewQuery.data.message ?? '空间模型物理表未就绪');
            }
          },
        },
      ]}><input /></Form.Item>
      <div className="management-filter-strip spatial-service-model-filters">
        <Input.Search allowClear value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索空间模型名称或编码" style={{ maxWidth: 360 }} />
        <Space><span>显示不可用模型</span><Switch size="small" checked={includeUnavailable} onChange={setIncludeUnavailable} disabled={readOnly} /></Space>
      </div>
      {!canViewModels && <Alert type="warning" showIcon message="没有模型查看权限，无法选择空间模型" />}
      {selectedModelId && previewQuery.data && !previewQuery.data.supported && (
        <Alert type="error" showIcon message={previewQuery.data.message ?? '空间模型物理表未就绪'} />
      )}
      {geometryPreview && !geometryPreview.spatialIndexAvailable && (
        <Alert type="warning" showIcon message="物理 Geometry 列未发现 GiST/SP-GiST 空间索引，允许发布，但范围查询性能可能较差" />
      )}
      <div className="management-results-surface spatial-service-model-results">
        <Table<SpatialDataServiceModelCandidate>
          size="small"
          className="management-table spatial-service-model-table"
          rowKey="id"
          loading={candidatesQuery.isFetching}
          dataSource={candidatesQuery.data?.content ?? []}
          pagination={false}
          scroll={{ y: '100%' }}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedModelId ? [selectedModelId] : [],
            getCheckboxProps: (candidate) => ({ disabled: readOnly || !candidate.selectable }),
            onChange: (keys) => form.setFieldValue('modelId', String(keys[0] ?? '')),
          }}
          columns={[
            { title: '空间模型', render: (_, item) => <><Typography.Text strong>{item.name}</Typography.Text><br /><Typography.Text type="secondary" code>{item.code}</Typography.Text></> },
            { title: '物理表', render: (_, item) => <Typography.Text code>{item.table}</Typography.Text> },
            { title: 'Geometry', render: (_, item) => item.geometryColumn ? <Space size={4}><Tag>{item.geometryKind}</Tag><code>{item.geometryColumn}</code></Space> : '—' },
            { title: 'CRS', width: 100, render: (_, item) => item.epsg ? `EPSG:${item.epsg}` : '—' },
            { title: '主键', dataIndex: 'primaryKeyColumn', width: 130, render: (value) => value ? <code>{value}</code> : '—' },
            { title: '状态', width: 170, render: (_, item) => item.selectable ? <Tag color="success">可发布</Tag> : <Tag color="warning">{item.unavailableReason ?? '不可用'}</Tag> },
          ]}
        />
      </div>
      {selected && <Typography.Paragraph className="spatial-service-selection-summary" type="secondary">
        发布后图层名：<Typography.Text code>{`svc_<服务编码>`}</Typography.Text>，数据源：{selected.dataSourceName ?? selected.dataSourceCode ?? selected.dataSourceId}
      </Typography.Paragraph>}
    </div>
  );
};
