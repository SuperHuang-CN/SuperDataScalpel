import { ApiOutlined, DatabaseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Card, Form, Input, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
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

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value));

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

  const columns: TableProps<ServiceEngine>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 190, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 160, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '管理地址', dataIndex: 'adminUrl', width: 275, ellipsis: true },
    { title: '公共地址', dataIndex: 'publicUrl', width: 275, ellipsis: true },
    { title: '使用状态', dataIndex: 'enabled', width: 100, render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作', key: 'action', width: 156, fixed: 'right',
      render: (_: unknown, engine: ServiceEngine) => (
        <Space size={2}>
          <Tooltip title="管理数据源"><Button type="text" size="small" aria-label={`管理${engine.name}数据源`} icon={<DatabaseOutlined />} onClick={() => setDataSourceEngine(engine)} /></Tooltip>
          {canUpdate && <Tooltip title="修改"><Button type="text" size="small" aria-label={`修改${engine.name}`} icon={<EditOutlined />} onClick={() => setEditingEngine(engine)} /></Tooltip>}
          {canTest && <Tooltip title="测试连接"><Button type="text" size="small" aria-label={`测试${engine.name}`} icon={<ApiOutlined />} loading={testMutation.isPending && testMutation.variables?.id === engine.id} onClick={() => void test(engine)} /></Tooltip>}
          {canDelete && <Popconfirm title="删除 Service Engine" description={`确认删除“${engine.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => remove(engine)}><Tooltip title="删除"><Button type="text" size="small" danger aria-label={`删除${engine.name}`} icon={<DeleteOutlined />} /></Tooltip></Popconfirm>}
        </Space>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      <Card className="management-card">
        <div className="management-toolbar">
          <Form<ServiceEngineFilters> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
            <Form.Item name="keyword" label="名称/编码"><Input allowClear placeholder="按名称或编码筛选" className="data-source-keyword-input" /></Form.Item>
            <Form.Item name="enabled" label="使用状态"><Select allowClear placeholder="全部" className="data-source-filter-select" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} /></Form.Item>
          </Form>
          <Space size={4} className="management-toolbar-actions">
            <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void enginesQuery.refetch()}>刷新</Button>
            {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
          </Space>
        </div>
        <Table<ServiceEngine>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={enginesQuery.data?.content ?? []} loading={enginesQuery.isFetching}
          scroll={{ x: 1330, y: '100%' }}
          pagination={{ current: page + 1, pageSize: size, total: enginesQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
        />
      </Card>
      <ServiceEngineDrawer open={createDrawerOpen || Boolean(editingEngine)} engine={editingEngine} canTest={canTest} onClose={closeDrawer} />
      <ServiceEngineDataSourceDrawer open={Boolean(dataSourceEngine)} engine={dataSourceEngine} canUpdate={canUpdate} canTest={canTest} canViewDataSources={canViewDataSources} onClose={() => setDataSourceEngine(null)} />
    </>
  );
};
