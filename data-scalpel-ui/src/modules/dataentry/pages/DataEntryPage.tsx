import { DatabaseOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
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
  const [candidateId, setCandidateId] = useState<string>();
  const [messageApi, contextHolder] = message.useMessage();
  const currentUser = useCurrentUser();
  const canManage = new Set(currentUser.data?.permissions ?? []).has('dataentry.manage');
  const formsQuery = useDataEntryForms({ ...filters, page, size });
  const candidatesQuery = useDataEntryCandidates(createOpen);
  const createMutation = useCreateDataEntryForm();

  const resetFilters = () => {
    form.resetFields();
    setFilters({});
    setPage(0);
  };

  const create = async () => {
    if (!candidateId) return;
    try {
      const detail = await createMutation.mutateAsync(candidateId);
      setCreateOpen(false);
      setCandidateId(undefined);
      navigate(`/data-entry/${detail.form.id}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '创建填报表单失败');
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
            { title: '模型', key: 'model', render: (_, row) => <ManagementListCell icon={<DatabaseOutlined />} primary={row.modelName ?? '模型已删除'} secondary={row.modelCode ?? row.modelId} /> },
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
      <Modal rootClassName="business-overlay business-modal-overlay" title="选择目标模型" open={createOpen} confirmLoading={createMutation.isPending} okButtonProps={{ disabled: !candidateId }} onOk={() => void create()} onCancel={() => { setCreateOpen(false); setCandidateId(undefined); }}>
        <Select
          showSearch optionFilterProp="label" value={candidateId} style={{ width: '100%' }} placeholder="选择尚未建立填报表单的模型"
          options={(candidatesQuery.data ?? []).map((candidate) => ({ value: candidate.modelId, label: `${candidate.modelName}（${candidate.modelCode}）${candidate.knownEligible ? '' : ' · 当前存在准入问题'}` }))}
          loading={candidatesQuery.isFetching} onChange={setCandidateId}
        />
      </Modal>
    </div>
  );
};
