import { FormOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Empty, Form, Modal, Select, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { useDataEntryCandidateFilters, useDataEntryCandidatePage } from '../hooks/useDataEntry';
import type { DataEntryModelCandidate } from '../model/dataEntry';

interface Filters { keyword?: string; layer?: string; storage?: string }
interface Props { loading: boolean; onCreate: (id: string) => Promise<void>; onClose: () => void }

const hasUnsupportedCapability = (row: DataEntryModelCandidate) => row.issues.some(issue =>
  ['TARGET_MODEL_NOT_MANAGED', 'TARGET_DATABASE_UNSUPPORTED', 'TARGET_FIELD_UNSUPPORTED'].includes(issue.code));

const CandidateAvailability = ({ row }: { row: DataEntryModelCandidate }) => {
  if (row.knownEligible) return <Tag color="success">基础检查通过</Tag>;
  const unsupported = hasUnsupportedCapability(row);
  return <div className="data-entry-candidate-issues">
    <Space size={4}>
      <Tag color={unsupported ? 'default' : 'warning'}>
        {unsupported ? '当前不支持填报' : '发布条件待完善'}
      </Tag>
      {row.issues.length > 0 && <ContextHelp ariaLabel={`${row.modelName}的填报检查说明`} presentation="popover"
        content={<ul>{row.issues.map((issue, index) => <li key={`${issue.code}-${index}`}>{issue.message}</li>)}</ul>} />}
    </Space>
    <span>{row.issues.length} 项原因 · 可创建草稿</span>
  </div>;
};

export const DataEntryCreateModal = ({ loading, onCreate, onClose }: Props) => {
  const [form] = Form.useForm<Filters>();
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<DataEntryModelCandidate>();
  const options = useDataEntryCandidateFilters();
  const query = useDataEntryCandidatePage({
    search: andSearch(
      orSearch(searchContains('name', filters.keyword?.trim()), searchContains('code', filters.keyword?.trim())),
      filters.layer === 'unassigned' ? 'warehouseLayerId:null' : searchEquals('warehouseLayerId', filters.layer),
      searchEquals('storageDataSourceId', filters.storage),
      searchEquals('physicalTableMode', 'MANAGED'),
    ),
    page, size: 10, sort: 'name,code,id',
  });
  const reset = () => { form.resetFields(); setFilters({}); setPage(0); };

  return <Modal open centered width={960}
    rootClassName="business-overlay business-modal-overlay workspace-resource-overlay resource-workspace-overlay data-entry-create-modal"
    title={<Space><FormOutlined />创建填报表单</Space>}
    onCancel={onClose} closable={!loading} mask={{ closable: !loading }} keyboard={!loading}
    footer={<div className="data-entry-create-footer">
      <div className="data-entry-selection-summary">{selected ? <>已选模型：<strong>{selected.modelName}</strong><span>{selected.modelCode}</span><Button type="link" disabled={loading} onClick={() => setSelected(undefined)}>清除</Button>
        {!selected.knownEligible && <div role="status">{hasUnsupportedCapability(selected) ? '此模型当前不能发布填报表单；创建后将保存为草稿。' : '将创建草稿，发布前仍需完善模型条件并通过完整检查。'}</div>}
      </> : '请选择一个目标模型'}</div>
      <Space><Button disabled={loading} onClick={onClose}>取消</Button><Button type="primary" loading={loading} disabled={!selected} onClick={() => selected && void onCreate(selected.modelId)}>创建草稿</Button></Space>
    </div>}>
    <p className="resource-form-help">仅显示尚未建立填报表单的受管模型。选择后创建草稿，发布前会检查模型、字段与物理表。</p>
    <div className="data-entry-candidate-filters">
      <Form form={form} layout="inline" autoComplete="off" onFinish={values => { setFilters(values); setPage(0); }}>
        <Form.Item name="keyword"><ManagementSearchInput allowClear aria-label="模型名称或编码" placeholder="搜索模型名称或编码" /></Form.Item>
        <Form.Item name="layer"><Select aria-label="数据分层" allowClear showSearch optionFilterProp="label" placeholder="全部分层" loading={options.isFetching}
          options={[...(options.data?.hasUnlayered ? [{ value: 'unassigned', label: '未分层' }] : []), ...(options.data?.layers ?? []).map(item => ({ value: item.id, label: item.name }))]} /></Form.Item>
        <Form.Item name="storage"><Select aria-label="绑定数据源" allowClear showSearch optionFilterProp="label" placeholder="全部绑定数据源" loading={options.isFetching}
          options={(options.data?.storages ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      </Form>
      <ManagementFilterActions form={form} appliedFilters={filters} loading={query.isFetching} onReset={reset} />
    </div>
    {options.isError && <InlineFeedback tone="error" label="筛选项加载失败，仍可按名称查询" action={<Button type="link" onClick={() => void options.refetch()}>重试</Button>} />}
    {query.isError && <InlineFeedback tone="error" label={query.error instanceof ApiError ? query.error.message : '候选模型加载失败'} action={<Button type="link" icon={<ReloadOutlined />} onClick={() => void query.refetch()}>重试</Button>} />}
    <Table<DataEntryModelCandidate> size="small" className="data-entry-candidate-table" rowKey="modelId" loading={query.isFetching}
      dataSource={query.data?.content ?? []} scroll={{ x: 780, y: 'min(42vh, 380px)' }}
      rowSelection={{ type: 'radio', selectedRowKeys: selected ? [selected.modelId] : [], preserveSelectedRowKeys: true,
        onSelect: row => setSelected(row), getCheckboxProps: row => ({ disabled: loading, 'aria-label': `选择模型 ${row.modelName}` }) }}
      columns={[
        { title: '模型 / 分层', width: 235, render: (_, row) => <div className="data-entry-candidate-identity"><strong>{row.modelName}</strong><span>{row.modelCode}</span><span>{row.warehouseLayerName ?? (row.warehouseLayerId ? '分层已失效' : '未分层')}</span></div> },
        { title: '数据存储 / 物理表', width: 270, render: (_, row) => <div className="data-entry-candidate-identity"><strong>{row.storageDataSourceName ?? row.storageDataSourceId}</strong><span>{[row.catalogName, row.schemaName, row.physicalTableName].filter(Boolean).join(' / ') || '—'}</span></div> },
        { title: '状态 / 版本', width: 100, render: (_, row) => <div className="data-entry-candidate-identity"><span>{({ DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' } as Record<string, string>)[row.modelStatus] ?? row.modelStatus}</span><span>v{row.schemaVersion}</span></div> },
        { title: '填报适用性', render: (_, row) => <CandidateAvailability row={row} /> },
      ]}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的未绑定受管模型" /> }}
      pagination={{ current: page + 1, pageSize: 10, total: query.data?.totalElements ?? 0, showSizeChanger: false, showTotal: total => `共 ${total} 个候选模型`, onChange: next => setPage(next - 1) }} />
  </Modal>;
};
