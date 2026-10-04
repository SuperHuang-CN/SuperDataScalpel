import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import '../../../shared/theme/resource-workspace.css';
import { DatabaseOutlined, EyeOutlined, FormOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { ConfigProvider, Button, Empty, Form, Select, Table, Tag, Tooltip, message } from 'antd';
import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import '../dataEntry.css';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell, ManagementName } from '../../../shared/components/ManagementListCells';
import { useCurrentUser } from '../../system';
import { useCreateDataEntryForm, useDataEntryForms } from '../hooks/useDataEntry';
import { DataEntryCreateModal } from '../components/DataEntryCreateModal';
import { dataEntryStatusLabels, type DataEntryForm, type DataEntryFormStatus } from '../model/dataEntry';

const statusColor: Record<DataEntryFormStatus, string> = { DRAFT: 'default', PUBLISHED: 'success', DISABLED: 'warning' };

export const DataEntryPage = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<{ keyword?: string; status?: DataEntryFormStatus }>();
  const [filters, setFilters] = useState<{ keyword?: string; status?: DataEntryFormStatus }>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [createOpen, setCreateOpen] = useState(false);
  const creatingRef = useRef(false);
  const [messageApi, contextHolder] = message.useMessage();
  const currentUser = useCurrentUser();
  const canManage = new Set(currentUser.data?.permissions ?? []).has('dataentry.manage');
  const formsQuery = useDataEntryForms({ ...filters, page, size });
  const createMutation = useCreateDataEntryForm();

  const resetFilters = () => {
    form.resetFields();
    setFilters({});
    setPage(0);
  };

  const create = async (candidateId: string) => {
    if (creatingRef.current) return;
    creatingRef.current = true;
    try {
      const detail = await createMutation.mutateAsync(candidateId);
      setCreateOpen(false);
      navigate(`/data-entry/${detail.form.id}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '创建填报表单失败');
    } finally {
      creatingRef.current = false;
    }
  };

  return (
    <ConfigProvider theme={workspaceResourceTheme}>
    <div className="management-page data-entry-page resource-workspace-list">
      {contextHolder}
      <section className="management-workbench">
        <div className="management-filter-strip">
        <Form autoComplete="off" form={form} layout="inline" className="management-filter-form" onFinish={(values) => { setFilters(values); setPage(0); }}>
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索模型名称或编码" /></Form.Item>
          <Form.Item name="status"><Select allowClear placeholder="表单状态" style={{ width: 140 }} options={Object.entries(dataEntryStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        </Form>
          <ManagementFilterActions form={form} appliedFilters={filters} loading={formsQuery.isFetching} onReset={resetFilters} />
          <div className="resource-list-commands">
            <Tooltip title="刷新"><Button aria-label="刷新填报表单" icon={<ReloadOutlined />} loading={formsQuery.isFetching} onClick={() => void formsQuery.refetch()} /></Tooltip>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建填报表单</Button>}
          </div>
        </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
          <span className="management-result-title"><FormOutlined aria-hidden />填报表单 <span className="management-result-count">共 {formsQuery.data?.totalElements ?? 0} 项</span></span>

        </div>
        {formsQuery.isError && <InlineFeedback tone="error" label={formsQuery.error instanceof ApiError ? formsQuery.error.message : '填报表单加载失败'} action={<Button onClick={() => void formsQuery.refetch()}>重试</Button>} />}
        <Table<DataEntryForm>
          scroll={{ x: 1040, y: '100%' }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={filters.keyword || filters.status ? '没有符合条件的填报表单' : '还没有填报表单'}>{filters.keyword || filters.status ? <Button onClick={resetFilters}>清空筛选</Button> : canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建填报表单</Button>}</Empty> }}
          className="management-table" rowKey="id" size="small" loading={formsQuery.isFetching} dataSource={formsQuery.data?.content ?? []}
          columns={[
            { title: '模型', key: 'model', width: 280, render: (_, row) => <ManagementListCell icon={<DatabaseOutlined />} primary={<ManagementName name={row.modelName ?? '模型已删除'} code={row.modelCode ?? row.modelId} description={row.modelDescription}><Button type="link" className="data-entry-model-link" onClick={() => navigate(`/data-entry/${row.id}`)}>{row.modelName ?? '模型已删除'}</Button></ManagementName>} secondary={row.modelCode !== row.modelName ? <Link to={`/data-entry/${row.id}`}>{row.modelCode ?? row.modelId}</Link> : undefined} /> },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color={statusColor[value as DataEntryFormStatus]}>{dataEntryStatusLabels[value as DataEntryFormStatus]}</Tag> },
            { title: '模型版本 / 发布版本', key: 'versions', width: 180, render: (_, row) => `${row.modelSchemaVersion ?? '—'} / ${row.publishedModelSchemaVersion ?? '—'}` },
            { title: '运行健康', key: 'health', width: 180, render: (_, row) => row.issues.length ? <Tooltip title={row.issues.map((issue) => issue.message).join('；')}><Tag color="warning">需检查 · {row.issues.length}</Tag></Tooltip> : row.healthSummary === 'DETAIL_CHECK_REQUIRED' ? <Tag color="processing">进入详情检查</Tag> : <Tag color="success">正常</Tag> },
            { title: '更新时间', dataIndex: 'updatedAt', width: 190, render: (value) => <ManagementDateTime value={value} /> },
            { title: '操作', key: 'actions', width: 80, fixed: 'right', render: (_, row) => <Tooltip title="进入填报详情"><Button type="text" icon={<EyeOutlined />} aria-label={`查看填报表单 ${row.modelName ?? row.id}`} onClick={() => navigate(`/data-entry/${row.id}`)} /></Tooltip> },
          ]}
          pagination={{ current: page + 1, pageSize: size, total: formsQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize !== size ? 0 : next - 1); setSize(nextSize); } }}
        />
      </div>
      </section>
      {createOpen && <DataEntryCreateModal loading={createMutation.isPending} onCreate={create} onClose={() => setCreateOpen(false)} />}
    </div>
    </ConfigProvider>
  );
};
