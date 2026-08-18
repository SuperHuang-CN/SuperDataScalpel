import { ApiOutlined, DatabaseOutlined, DeleteOutlined, EditOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Select, Space, Table, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ServiceEngineDrawer } from './ServiceEngineDrawer';
import { ServiceEngineDataSourceDrawer } from './ServiceEngineDataSourceDrawer';
import { useDeleteServiceEngine, useServiceEngines, useTestServiceEngine } from '../hooks/useServiceEngines';
import type { ServiceEngine, ServiceEngineFilters } from '../model/serviceEngine';
import { buildServiceEngineSearch } from '../model/serviceEngineSearch';

const DEFAULT_PAGE_SIZE = 20;

interface ServiceEngineManagementPanelProps {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canTest: boolean;
  canViewDataSources: boolean;
}

export const ServiceEngineManagementPanel = ({ canCreate, canUpdate, canDelete, canTest, canViewDataSources }: ServiceEngineManagementPanelProps) => {
  const [filterForm] = Form.useForm<ServiceEngineFilters>();
  const [filters, setFilters] = useState<ServiceEngineFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingEngine, setEditingEngine] = useState<ServiceEngine | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [dataSourceEngine, setDataSourceEngine] = useState<ServiceEngine | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const request = useMemo(() => ({ search: buildServiceEngineSearch(filters), page, size, sort: '-updatedAt,code' }), [filters, page, size]);
  const enginesQuery = useServiceEngines(request);
  const deleteMutation = useDeleteServiceEngine();
  const testMutation = useTestServiceEngine();

  const search = (nextFilters: ServiceEngineFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const closeDrawer = () => {
    setEditingEngine(null);
    setCreateDrawerOpen(false);
  };

  const test = async (engine: ServiceEngine) => {
    try {
      const result = await testMutation.mutateAsync({ id: engine.id });
      messageApi.success(`${engine.name} 连接成功，支持：${result.databaseTypes.join('、') || '无'}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '测试 Service Engine 失败');
    }
  };

  const remove = async (engine: ServiceEngine) => {
    try {
      await deleteMutation.mutateAsync(engine.id);
      messageApi.success('Service Engine 已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除 Service Engine 失败');
    }
  };
  const confirmRemove = (engine: ServiceEngine) => Modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除 Service Engine',
    content: `确认删除“${engine.name}”吗？`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => remove(engine),
  });

  const columns: TableProps<ServiceEngine>['columns'] = [
    { title: '引擎', dataIndex: 'name', width: 240, render: (value: string, engine) => <ManagementListCell icon={<ApiOutlined />} iconTone="cyan" primary={value} secondary={<ManagementCode value={engine.code} />} /> },
    {
      title: '说明',
      dataIndex: 'description',
      render: (value?: string) => (
        <ManagementListCell
          className="service-engine-description-cell"
          primary={value ? <Tooltip title={value}><span>{value}</span></Tooltip> : '—'}
        />
      ),
    },
    { title: '访问地址', width: 390, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementCode value={engine.adminUrl} title="管理地址" />} secondary={<ManagementCode value={engine.publicUrl} title="公共地址" />} /> },
    { title: '状态 / 凭据', width: 170, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementStatusIndicator label={engine.enabled ? '启用' : '停用'} tone={engine.enabled ? 'success' : 'default'} />} secondary={engine.managementTokenConfigured ? '管理 Token 已配置' : '未配置管理 Token'} /> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作', key: 'action', width: 112,
      render: (_: unknown, engine: ServiceEngine) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="管理数据源"><Button type="text" size="small" aria-label={`管理${engine.name}数据源`} icon={<DatabaseOutlined />} onClick={() => setDataSourceEngine(engine)} /></Tooltip>
            {canUpdate && <Tooltip title="修改"><Button type="text" size="small" aria-label={`修改${engine.name}`} icon={<EditOutlined />} onClick={() => setEditingEngine(engine)} /></Tooltip>}
          </div>
          <Dropdown trigger={['click']} menu={{ items: [
            { key: 'datasources', label: '管理数据源', icon: <DatabaseOutlined /> },
            ...(canUpdate ? [{ key: 'edit', label: '修改', icon: <EditOutlined /> }] : []),
            ...(canTest ? [{ key: 'test', label: '测试连接', icon: <ApiOutlined /> }] : []),
            ...(canDelete ? [{ type: 'divider' as const }, { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }] : []),
          ], onClick: ({ key }) => { if (key === 'datasources') setDataSourceEngine(engine); if (key === 'edit') setEditingEngine(engine); if (key === 'test') void test(engine); if (key === 'delete') confirmRemove(engine); } }}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" size="small" aria-label={`${engine.name}的更多操作`} icon={<MoreOutlined />} loading={testMutation.isPending && testMutation.variables?.id === engine.id} /></Tooltip>
          </Dropdown>
        </div>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<ServiceEngineFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
            <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索引擎名称或编码" className="data-source-keyword-input" /></Form.Item>
            <Form.Item name="enabled"><Select allowClear placeholder="全部状态" className="data-source-filter-select" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} /></Form.Item>
          </Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={enginesQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">服务引擎 <span className="management-result-count">共 {enginesQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新服务引擎列表" onClick={() => void enginesQuery.refetch()} /></Tooltip>
            {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
          </Space>
          </div>
          <Table<ServiceEngine>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={enginesQuery.data?.content ?? []} loading={enginesQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{ current: page + 1, pageSize: size, total: enginesQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
          />
        </div>
      </section>
      <ServiceEngineDrawer open={createDrawerOpen || Boolean(editingEngine)} engine={editingEngine} canTest={canTest} onClose={closeDrawer} />
      <ServiceEngineDataSourceDrawer open={Boolean(dataSourceEngine)} engine={dataSourceEngine} canUpdate={canUpdate} canTest={canTest} canViewDataSources={canViewDataSources} onClose={() => setDataSourceEngine(null)} />
    </>
  );
};
