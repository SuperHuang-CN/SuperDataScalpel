import { DatabaseOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Popover, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { DataModelPickerModal, type DataModelPickerCandidate } from '../../model';
import { useCurrentUser } from '../../system';
import { useCreateDataEntryForm, useDataEntryCandidates, useDataEntryForms } from '../hooks/useDataEntry';
import { dataEntryStatusLabels, type DataEntryForm, type DataEntryFormStatus } from '../model/dataEntry';

const statusColor: Record<DataEntryFormStatus, string> = { DRAFT: 'default', PUBLISHED: 'success', DISABLED: 'warning' };

export const DataEntryPage = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<{ keyword?: string; status?: DataEntryFormStatus }>();
  const [filters, setFilters] = useState<{ keyword?: string; status?: DataEntryFormStatus }>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [createOpen, setCreateOpen] = useState(false);
  const [candidateKeyword, setCandidateKeyword] = useState('');
  const creatingRef = useRef(false);
  const [messageApi, contextHolder] = message.useMessage();
  const currentUser = useCurrentUser();
  const canManage = new Set(currentUser.data?.permissions ?? []).has('dataentry.manage');
  const formsQuery = useDataEntryForms({ ...filters, page, size });
  const candidatesQuery = useDataEntryCandidates(createOpen, candidateKeyword);
  const createMutation = useCreateDataEntryForm();
  const candidates = useMemo<DataModelPickerCandidate[]>(() => (candidatesQuery.data ?? [])
    .filter((candidate) => candidate.modelStatus === 'PUBLISHED')
    .map((candidate) => ({
      id: candidate.modelId,
      name: candidate.modelName,
      code: candidate.modelCode,
      storageDataSourceName: candidate.storageDataSourceName,
      warehouseLayer: candidate.warehouseLayer,
      disabled: !candidate.knownEligible,
      extra: !candidate.knownEligible && (
        <Popover
          trigger={['hover', 'click', 'focus']}
          title={<OverlayTitle variant="popover" title="当前准入问题" />}
          content={<Space orientation="vertical" size={4} style={{ maxWidth: 360 }}>
            {candidate.issues.map((issue, index) => <span key={`${issue.code}-${index}`}>{issue.message}</span>)}
          </Space>}
        >
          <Button
            type="text"
            size="small"
            className="resource-picker-issue-count"
            aria-label={`${candidate.modelName} 有 ${candidate.issues.length} 项准入问题`}
            onClick={(event) => event.stopPropagation()}
          >
            {candidate.issues.length}
          </Button>
        </Popover>
      ),
    })), [candidatesQuery.data]);

  const resetFilters = () => {
    form.resetFields();
    setFilters({});
    setPage(0);
  };

  const create = async (modelId: string) => {
    if (creatingRef.current) return;
    creatingRef.current = true;
    try {
      const detail = await createMutation.mutateAsync(modelId);
      setCreateOpen(false);
      setCandidateKeyword('');
      navigate(`/data-entry/${detail.form.id}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '创建填报表单失败');
    } finally {
      creatingRef.current = false;
    }
  };

  return (
    <div className="management-page data-entry-page">
      {contextHolder}
      <section className="management-workbench">
        <div className="management-filter-strip">
        <Form autoComplete="off" form={form} layout="inline" className="management-filter-form" onFinish={(values) => { setFilters(values); setPage(0); }}>
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索模型名称或编码" /></Form.Item>
          <Form.Item name="status"><Select allowClear placeholder="表单状态" style={{ width: 140 }} options={Object.entries(dataEntryStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        </Form>
          <ManagementFilterActions form={form} appliedFilters={filters} loading={formsQuery.isFetching} onReset={resetFilters} />
        </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
          <span className="management-result-title">填报表单 <span className="management-result-count">共 {formsQuery.data?.totalElements ?? 0} 项</span></span>
          <Space className="management-result-actions">
            <Tooltip title="刷新"><Button type="text" aria-label="刷新填报表单" icon={<ReloadOutlined />} loading={formsQuery.isFetching} onClick={() => void formsQuery.refetch()} /></Tooltip>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建填报表单</Button>}
          </Space>
        </div>
        <Table<DataEntryForm>
          className="management-table" rowKey="id" size="small" loading={formsQuery.isFetching} dataSource={formsQuery.data?.content ?? []}
          columns={[
            { title: '模型', key: 'model', render: (_, row) => <ManagementListCell icon={<DatabaseOutlined />} primary={<Link to={`/data-entry/${row.id}`}>{row.modelName ?? '模型已删除'}</Link>} secondary={<Link to={`/data-entry/${row.id}`}>{row.modelCode ?? row.modelId}</Link>} /> },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={statusColor[value as DataEntryFormStatus]}>{dataEntryStatusLabels[value as DataEntryFormStatus]}</Tag> },
            { title: '模型版本 / 发布版本', key: 'versions', width: 180, render: (_, row) => `${row.modelSchemaVersion ?? '—'} / ${row.publishedModelSchemaVersion ?? '—'}` },
            { title: '运行健康', key: 'health', width: 260, render: (_, row) => row.issues.length ? <Tooltip title={row.issues.map((issue) => issue.message).join('；')}><Tag color="warning">需检查 · {row.issues.length}</Tag></Tooltip> : row.healthSummary === 'DETAIL_CHECK_REQUIRED' ? <Tag color="processing">进入详情检查</Tag> : <Tag color="success">正常</Tag> },
            { title: '更新时间', dataIndex: 'updatedAt', width: 190, render: (value) => <ManagementDateTime value={value} /> },
            { title: '操作', key: 'actions', width: 72, render: (_, row) => <Dropdown menu={{ items: [{ key: 'detail', label: '进入填报详情' }], onClick: () => navigate(`/data-entry/${row.id}`) }}><Button type="text" icon={<MoreOutlined />} aria-label={`操作填报表单 ${row.modelName ?? row.id}`} /></Dropdown> },
          ]}
          pagination={{ current: page + 1, pageSize: size, total: formsQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize !== size ? 0 : next - 1); setSize(nextSize); } }}
        />
      </div>
      </section>
      <DataModelPickerModal
        open={createOpen}
        value={[]}
        title="选择目标模型"
        rootClassName="business-overlay business-modal-overlay"
        confirmLoading={createMutation.isPending}
        source={{
          candidates,
          loading: candidatesQuery.isFetching,
          error: candidatesQuery.isError,
          onSearch: setCandidateKeyword,
          onRetry: () => { void candidatesQuery.refetch(); },
          heading: '已发布且尚未建表单的模型',
          emptyText: '没有匹配的候选模型',
          limit: 100,
        }}
        onCancel={() => { setCreateOpen(false); setCandidateKeyword(''); }}
        onConfirm={(modelIds) => { if (modelIds[0]) void create(modelIds[0]); }}
      />
    </div>
  );
};
