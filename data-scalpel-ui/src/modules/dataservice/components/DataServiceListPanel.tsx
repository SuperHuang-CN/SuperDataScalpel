import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  FilterOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Badge, Button, Card, Form, Input, Popconfirm, Popover, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useDataModels } from '../../model';
import { useServiceEngines } from '../../serviceengine';
import { DataServiceDrawer } from './DataServiceDrawer';
import {
  useDataServices,
  useDeleteDataService,
  useDisableDataService,
  usePublishDataService,
} from '../hooks/useDataServices';
import {
  dataServiceDeploymentStatusLabels,
  dataServiceStatusLabels,
  type DataService,
  type DataServiceDeploymentStatus,
  type DataServiceFilters,
  type DataServiceStatus,
} from '../model/dataService';
import { buildDataServiceSearch } from '../model/dataServiceSearch';
import { buildDataServiceCurlCommand } from '../model/dataServiceCurl';

const DEFAULT_PAGE_SIZE = 20;

interface DataServiceListPanelProps {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canPublish: boolean;
  canViewDirectories: boolean;
  canManageDirectories: boolean;
  canViewModels: boolean;
  canViewEngines: boolean;
}

const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

const serviceStatusColors: Record<DataServiceStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const deploymentStatusColors: Record<DataServiceDeploymentStatus, string> = {
  PENDING: 'processing',
  DEPLOYED: 'success',
  FAILED: 'error',
  REMOVING: 'processing',
  REMOVED: 'default',
};

const statusOptions = Object.entries(dataServiceStatusLabels).map(([value, label]) => ({ value, label }));

export const DataServiceListPanel = ({
  canCreate,
  canUpdate,
  canDelete,
  canPublish,
  canViewDirectories,
  canManageDirectories,
  canViewModels,
  canViewEngines,
}: DataServiceListPanelProps) => {
  const [filterForm] = Form.useForm<DataServiceFilters>();
  const selectedEngineId = Form.useWatch('engineId', filterForm);
  const selectedModelId = Form.useWatch('modelId', filterForm);
  const [filters, setFilters] = useState<DataServiceFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingDataService, setEditingDataService] = useState<DataService | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const directoriesQuery = useDirectoryTree('DATA_SERVICE', canViewDirectories);
  const enginesQuery = useServiceEngines({ page: 0, size: 500, sort: 'code' }, canViewEngines);
  const modelsQuery = useDataModels({ page: 0, size: 500, sort: 'code' }, canViewModels);
  const request = useMemo(() => ({
    search: buildDataServiceSearch(filters), page, size, sort: '-updatedAt,code',
  }), [filters, page, size]);
  const dataServicesQuery = useDataServices(request);
  const deleteMutation = useDeleteDataService();
  const publishMutation = usePublishDataService();
  const disableMutation = useDisableDataService();
  const engineNames = new Map((enginesQuery.data?.content ?? []).map((engine) => [engine.id, engine.name]));
  const enginePublicUrls = new Map((enginesQuery.data?.content ?? []).map((engine) => [engine.id, engine.publicUrl]));
  const modelNames = new Map((modelsQuery.data?.content ?? []).map((model) => [model.id, model.name]));

  const search = (nextFilters: DataServiceFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    setDirectorySelection(undefined);
    search({});
  };

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    if (selection === undefined) {
      search({ ...filters, directoryIds: undefined, uncategorized: undefined });
      return;
    }
    if (selection === null) {
      search({ ...filters, directoryIds: undefined, uncategorized: true });
      return;
    }
    search({
      ...filters,
      directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], selection),
      uncategorized: undefined,
    });
  };

  const closeDrawer = () => {
    setEditingDataService(null);
    setCreateDrawerOpen(false);
  };

  const publish = async (dataService: DataService) => {
    try {
      const response = await publishMutation.mutateAsync(dataService.id);
      if (response.status === 'PUBLISHED' && response.deploymentStatus === 'DEPLOYED') {
        messageApi.success(`${dataService.name} 已发布`);
      } else {
        messageApi.error(response.deploymentError || 'Engine 未确认部署，请查看部署状态');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '发布数据服务失败');
    }
  };

  const disable = async (dataService: DataService) => {
    try {
      const response = await disableMutation.mutateAsync(dataService.id);
      if (response.status === 'DISABLED' && response.deploymentStatus === 'REMOVED') {
        messageApi.success(`${dataService.name} 已下线`);
      } else {
        messageApi.error(response.deploymentError || 'Engine 未确认下线，请查看部署状态');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下线数据服务失败');
    }
  };

  const remove = async (dataService: DataService) => {
    try {
      await deleteMutation.mutateAsync(dataService.id);
      messageApi.success('数据服务已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除数据服务失败');
    }
  };

  const copyCurl = async (dataService: DataService) => {
    const publicUrl = enginePublicUrls.get(dataService.engineId);
    if (!publicUrl) {
      messageApi.error('未找到 Service Engine 的公网地址');
      return;
    }

    if (!navigator.clipboard) {
      messageApi.error('当前浏览器不支持自动复制，请使用 HTTPS 或 localhost 访问');
      return;
    }

    try {
      await navigator.clipboard.writeText(buildDataServiceCurlCommand(publicUrl, dataService.routePath));
      messageApi.success('访问 cURL 已复制');
    } catch {
      messageApi.error('复制失败，请检查浏览器的剪贴板权限');
    }
  };

  const columns: TableProps<DataService>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 170, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 165, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '模型', dataIndex: 'modelId', width: 160, ellipsis: true, render: (value: string) => modelNames.get(value) ?? value },
    { title: 'Engine', dataIndex: 'engineId', width: 160, ellipsis: true, render: (value: string) => engineNames.get(value) ?? value },
    { title: '公开路由', dataIndex: 'routePath', width: 235, ellipsis: true, render: (value: string) => <code>{value}</code> },
    {
      title: '服务状态', dataIndex: 'status', width: 100,
      render: (value: DataServiceStatus) => <Tag color={serviceStatusColors[value]}>{dataServiceStatusLabels[value]}</Tag>,
    },
    {
      title: '部署状态', key: 'deploymentStatus', width: 118,
      render: (_: unknown, dataService: DataService) => dataService.deploymentStatus ? (
        <Tooltip title={dataService.deploymentError || undefined}>
          <Tag color={deploymentStatusColors[dataService.deploymentStatus]}>
            {dataServiceDeploymentStatusLabels[dataService.deploymentStatus]}
          </Tag>
        </Tooltip>
      ) : '—',
    },
    { title: '版本', dataIndex: 'revision', width: 74, align: 'right' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作', key: 'action', width: 142, fixed: 'right',
      render: (_: unknown, dataService: DataService) => (
        <Space size={2}>
          {canUpdate && dataService.status !== 'PUBLISHED' && <Tooltip title="修改"><Button type="text" size="small" aria-label={`修改${dataService.name}`} icon={<EditOutlined />} onClick={() => setEditingDataService(dataService)} /></Tooltip>}
          {dataService.status === 'PUBLISHED' && dataService.deploymentStatus === 'DEPLOYED' && enginePublicUrls.has(dataService.engineId) && <Tooltip title="复制访问 cURL"><Button type="text" size="small" aria-label={`复制${dataService.name}的访问 cURL`} icon={<CopyOutlined />} onClick={() => void copyCurl(dataService)} /></Tooltip>}
          {canPublish && dataService.status !== 'PUBLISHED' && <Tooltip title="发布"><Button type="text" size="small" aria-label={`发布${dataService.name}`} icon={<UploadOutlined />} loading={publishMutation.isPending && publishMutation.variables === dataService.id} onClick={() => void publish(dataService)} /></Tooltip>}
          {canPublish && dataService.status === 'PUBLISHED' && <Tooltip title="下线"><Button type="text" size="small" aria-label={`下线${dataService.name}`} icon={<StopOutlined />} loading={disableMutation.isPending && disableMutation.variables === dataService.id} onClick={() => void disable(dataService)} /></Tooltip>}
          {canDelete && <Popconfirm title="删除数据服务" description={`确认删除“${dataService.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => remove(dataService)}><Tooltip title="删除"><Button type="text" size="small" danger aria-label={`删除${dataService.name}`} icon={<DeleteOutlined />} /></Tooltip></Popconfirm>}
        </Space>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="DATA_SERVICE" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <Card className="management-card">
          <div className="management-toolbar">
            <Form<DataServiceFilters> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
              <Form.Item name="keyword" label="名称/编码"><Input allowClear placeholder="按名称或编码筛选" className="data-source-keyword-input" /></Form.Item>
              <Form.Item name="status" label="服务状态"><Select allowClear placeholder="全部" options={statusOptions} className="data-source-filter-select" /></Form.Item>
              <Popover
                trigger="click"
                placement="bottomLeft"
                content={(
                  <div className="advanced-filter-popover">
                    <div className="advanced-filter-title">更多筛选</div>
                    <Form.Item name="engineId" label="Service Engine"><Select allowClear showSearch optionFilterProp="label" options={(enginesQuery.data?.content ?? []).map((engine) => ({ value: engine.id, label: engine.name }))} className="advanced-filter-select" /></Form.Item>
                    <Form.Item name="modelId" label="模型"><Select allowClear showSearch optionFilterProp="label" options={(modelsQuery.data?.content ?? []).map((model) => ({ value: model.id, label: model.name }))} className="advanced-filter-select" /></Form.Item>
                    <div className="advanced-filter-actions"><Button type="link" size="small" htmlType="button" onClick={() => filterForm.setFieldsValue({ engineId: undefined, modelId: undefined })}>清空更多条件</Button></div>
                  </div>
                )}
              >
                <Badge count={Number(Boolean(selectedEngineId)) + Number(Boolean(selectedModelId))} size="small" offset={[-2, 2]}><Button icon={<FilterOutlined />}>更多</Button></Badge>
              </Popover>
            </Form>
            <Space size={4} className="management-toolbar-actions">
              <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void dataServicesQuery.refetch()}>刷新</Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
            </Space>
          </div>
          <Table<DataService>
            size="small" className="management-table" rowKey="id" columns={columns}
            dataSource={dataServicesQuery.data?.content ?? []} loading={dataServicesQuery.isFetching}
            scroll={{ x: 1500, y: '100%' }}
            pagination={{ current: page + 1, pageSize: size, total: dataServicesQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
            onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
          />
        </Card>
      </div>
      <DataServiceDrawer open={createDrawerOpen || Boolean(editingDataService)} dataService={editingDataService} canViewDirectories={canViewDirectories} canViewModels={canViewModels} canViewEngines={canViewEngines} onClose={closeDrawer} />
    </>
  );
};
