import { ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Form, Input, Select, Space, Table, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { buildDataModelSearch, dataModelStatusLabels, physicalTableModeLabels, type DataModelStatus, type PhysicalTableMode } from '../../model';
import { buildTaskSearch, taskStatusColors, taskStatusLabels, taskTypeLabels, type TaskStatus, type TaskType } from '../../task';
import { buildDataServiceSearch, dataServiceStatusLabels, dataServiceTypeLabels, type DataServiceStatus, type DataServiceType } from '../../dataservice';
import {
  useDataSourceRelatedModels,
  useDataSourceRelatedServices,
  useDataSourceRelatedTasks,
} from '../hooks/useDataSources';
import type {
  DataSourceRelatedModel,
  DataSourceRelatedService,
  DataSourceRelatedTask,
  DataSourceRelationKind,
  DataSourceTaskReferenceLocation,
  DataSourceTaskRelationRole,
} from '../model/dataSource';

const relationKindLabels: Record<DataSourceRelationKind, string> = {
  DIRECT: '直接数据源',
  VIA_MODEL: '经模型',
};

const roleLabels: Record<DataSourceTaskRelationRole, string> = {
  INPUT: '输入',
  OUTPUT: '输出',
};

const DEFAULT_SORT = '-updatedAt,name';

interface PanelProps {
  dataSourceId: string;
  active: boolean;
}

interface ModelFilters {
  keyword?: string;
  status?: DataModelStatus;
  physicalTableMode?: PhysicalTableMode;
}

const relationTags = (values: DataSourceRelationKind[]) => (
  <Space size={[4, 4]} wrap>
    {values.map((value) => <Tag key={value} color={value === 'DIRECT' ? 'blue' : 'purple'}>{relationKindLabels[value]}</Tag>)}
  </Space>
);

const errorAlert = (title: string, retry: () => void) => (
  <Alert showIcon type="error" message={title} description="请稍后重试。" action={<Button size="small" onClick={retry}>重试</Button>} />
);

export const DataSourceRelatedModelsPanel = ({ dataSourceId, active }: PanelProps) => {
  const navigate = useNavigate();
  const [form] = Form.useForm<ModelFilters>();
  const [filters, setFilters] = useState<ModelFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState(DEFAULT_SORT);
  const request = useMemo(() => ({
    search: buildDataModelSearch({
      keyword: filters.keyword,
      status: filters.status,
      physicalTableModes: filters.physicalTableMode ? [filters.physicalTableMode] : undefined,
    }),
    page: page - 1,
    size: pageSize,
    sort,
  }), [filters, page, pageSize, sort]);
  const query = useDataSourceRelatedModels(dataSourceId, request, active);
  const columns: TableProps<DataSourceRelatedModel>['columns'] = [
    { title: '模型', dataIndex: 'modelName', width: 220, sorter: true, render: (value: string, model) => <Button type="link" onClick={() => navigate(`/model/${model.modelId}`)}>{value}</Button> },
    { title: '编码', dataIndex: 'modelCode', width: 170, render: (value: string) => <code>{value}</code> },
    { title: '状态', dataIndex: 'status', width: 100, sorter: true, render: (value: DataModelStatus) => <Tag>{dataModelStatusLabels[value]}</Tag> },
    { title: '模式', dataIndex: 'physicalTableMode', width: 100, sorter: true, render: (value: PhysicalTableMode) => physicalTableModeLabels[value] },
    { title: '物理表', width: 220, render: (_, model) => <code>{model.physicalTableName}</code> },
    { title: 'Schema', dataIndex: 'schemaVersion', width: 90, render: (value: number) => `v${value}` },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, sorter: true, render: formatManagementDateTime },
  ];

  return <div className="data-source-detail-tab-panel">
    <div className="data-source-related-toolbar">
      <Form<ModelFilters> autoComplete="off" form={form} layout="inline" onFinish={(values) => { setFilters(values); setPage(1); }}>
        <Form.Item name="keyword" label="名称"><Input allowClear placeholder="名称或编码" className="data-source-related-keyword" /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(dataModelStatusLabels) as [DataModelStatus, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="physicalTableMode" label="模式"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(physicalTableModeLabels) as [PhysicalTableMode, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
      </Form>
      <Space size={4}>
        <Button type="primary" onClick={() => form.submit()}>查询</Button>
        <Button onClick={() => { form.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
        <Tooltip title="刷新关联模型"><Button icon={<ReloadOutlined />} aria-label="刷新关联模型" loading={query.isFetching} onClick={() => void query.refetch()} /></Tooltip>
      </Space>
    </div>
    {query.error && errorAlert('关联模型加载失败', () => void query.refetch())}
    <Table<DataSourceRelatedModel>
      className="management-table" size="small" rowKey="modelId" loading={query.isFetching}
      dataSource={query.data?.content ?? []} columns={columns} scroll={{ x: 1_140, y: '100%' }}
      pagination={{ current: page, pageSize, total: query.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
      onChange={(pagination, _filters, sorter) => {
        setPage(pagination.current ?? 1); setPageSize(pagination.pageSize ?? 20);
        if (!Array.isArray(sorter) && sorter.field) {
          const fields: Record<string, string> = {
            modelName: 'name', status: 'status', physicalTableMode: 'physicalTableMode', updatedAt: 'updatedAt',
          };
          setSort(`${sorter.order === 'descend' ? '-' : ''}${fields[String(sorter.field)] ?? 'updatedAt'},name`);
        }
      }}
    />
  </div>;
};

interface TaskFilters {
  keyword?: string;
  type?: TaskType;
  status?: TaskStatus;
  role?: DataSourceTaskRelationRole;
  relationKind?: DataSourceRelationKind;
}

const LocationTags = ({ locations }: { locations: DataSourceTaskReferenceLocation[] }) => {
  const visible = locations.slice(0, 3);
  return <Space size={[4, 4]} wrap>
    {visible.map((location) => (
      <Tooltip key={`${location.relationKind}-${location.locationKey}-${location.role}`} title={location.resourceLabel || location.locationLabel}>
        <Tag>{location.locationLabel}{location.resourceLabel ? ` · ${location.resourceLabel}` : ''}</Tag>
      </Tooltip>
    ))}
    {locations.length > visible.length && <Tooltip title={locations.slice(3).map((item) => item.locationLabel).join('、')}><Tag>+{locations.length - visible.length}</Tag></Tooltip>}
  </Space>;
};

export const DataSourceRelatedTasksPanel = ({ dataSourceId, active }: PanelProps) => {
  const navigate = useNavigate();
  const [form] = Form.useForm<TaskFilters>();
  const [filters, setFilters] = useState<TaskFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState(DEFAULT_SORT);
  const request = useMemo(() => ({
    search: buildTaskSearch({ keyword: filters.keyword, type: filters.type, status: filters.status }),
    page: page - 1, size: pageSize, sort,
  }), [filters, page, pageSize, sort]);
  const query = useDataSourceRelatedTasks(dataSourceId, filters.role, filters.relationKind, request, active);
  const columns: TableProps<DataSourceRelatedTask>['columns'] = [
    { title: '任务', dataIndex: 'taskName', width: 220, sorter: true, render: (value: string, task) => <Button type="link" onClick={() => navigate(`/task/${task.taskId}`)}>{value}</Button> },
    { title: '关联方式', dataIndex: 'relationKinds', width: 170, render: relationTags },
    { title: '角色', dataIndex: 'roles', width: 120, render: (roles: DataSourceTaskRelationRole[]) => <Space size={4}>{roles.map((role) => <Tag key={role} color={role === 'INPUT' ? 'blue' : 'purple'}>{roleLabels[role]}</Tag>)}</Space> },
    { title: '任务类型', dataIndex: 'taskType', width: 150, sorter: true, render: (value: TaskType) => taskTypeLabels[value] },
    { title: '状态', dataIndex: 'taskStatus', width: 100, sorter: true, render: (value: TaskStatus) => <Tag color={taskStatusColors[value]}>{taskStatusLabels[value]}</Tag> },
    { title: '引用位置', dataIndex: 'locations', width: 360, render: (locations: DataSourceTaskReferenceLocation[]) => <LocationTags locations={locations} /> },
    { title: '定义版本', dataIndex: 'definitionVersion', width: 100, render: (value: number) => value ? `v${value}` : '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, sorter: true, render: formatManagementDateTime },
  ];

  return <div className="data-source-detail-tab-panel">
    <div className="data-source-related-toolbar">
      <Form<TaskFilters> autoComplete="off" form={form} layout="inline" onFinish={(values) => { setFilters(values); setPage(1); }}>
        <Form.Item name="keyword" label="名称"><Input allowClear placeholder="筛选任务" className="data-source-related-keyword" /></Form.Item>
        <Form.Item name="type" label="类型"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(taskTypeLabels) as [TaskType, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(taskStatusLabels) as [TaskStatus, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="role" label="角色"><Select allowClear placeholder="全部" className="data-source-related-select" options={Object.entries(roleLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="relationKind" label="关系"><Select allowClear placeholder="全部" className="data-source-related-select" options={Object.entries(relationKindLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      </Form>
      <Space size={4}>
        <Button type="primary" onClick={() => form.submit()}>查询</Button>
        <Button onClick={() => { form.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
        <Tooltip title="刷新关联任务"><Button icon={<ReloadOutlined />} aria-label="刷新关联任务" loading={query.isFetching} onClick={() => void query.refetch()} /></Tooltip>
      </Space>
    </div>
    {query.error && errorAlert('关联任务加载失败', () => void query.refetch())}
    <Table<DataSourceRelatedTask>
      className="management-table" size="small" rowKey="taskId" loading={query.isFetching}
      dataSource={query.data?.content ?? []} columns={columns} scroll={{ x: 1_400, y: '100%' }}
      pagination={{ current: page, pageSize, total: query.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
      onChange={(pagination, _filters, sorter) => {
        setPage(pagination.current ?? 1); setPageSize(pagination.pageSize ?? 20);
        if (!Array.isArray(sorter) && sorter.field) {
          const fields: Partial<Record<keyof DataSourceRelatedTask, string>> = { taskName: 'name', taskType: 'type', taskStatus: 'status', updatedAt: 'updatedAt' };
          setSort(`${sorter.order === 'descend' ? '-' : ''}${fields[sorter.field as keyof DataSourceRelatedTask] ?? 'updatedAt'},name`);
        }
      }}
    />
  </div>;
};

interface ServiceFilters {
  keyword?: string;
  type?: DataServiceType;
  status?: DataServiceStatus;
  relationKind?: DataSourceRelationKind;
}

export const DataSourceRelatedServicesPanel = ({ dataSourceId, active }: PanelProps) => {
  const navigate = useNavigate();
  const [form] = Form.useForm<ServiceFilters>();
  const [filters, setFilters] = useState<ServiceFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState(DEFAULT_SORT);
  const request = useMemo(() => ({
    search: buildDataServiceSearch({ keyword: filters.keyword, type: filters.type, status: filters.status }),
    page: page - 1, size: pageSize, sort,
  }), [filters, page, pageSize, sort]);
  const query = useDataSourceRelatedServices(dataSourceId, filters.relationKind, request, active);
  const columns: TableProps<DataSourceRelatedService>['columns'] = [
    { title: '数据服务', dataIndex: 'serviceName', width: 220, sorter: true, render: (value: string, service) => <Button type="link" onClick={() => navigate(`/dataservice/${service.serviceId}`)}>{value}</Button> },
    { title: '编码', dataIndex: 'serviceCode', width: 170, render: (value: string) => <code>{value}</code> },
    { title: '关联方式', dataIndex: 'relationKinds', width: 170, render: relationTags },
    { title: '类型', dataIndex: 'serviceType', width: 130, sorter: true, render: (value: DataServiceType) => dataServiceTypeLabels[value] },
    { title: '状态', dataIndex: 'status', width: 100, sorter: true, render: (value: DataServiceStatus) => <Tag>{dataServiceStatusLabels[value]}</Tag> },
    { title: '路由', dataIndex: 'routePath', width: 250, render: (value: string) => <code>{value}</code> },
    { title: '定义版本', dataIndex: 'definitionVersion', width: 100, render: (value: number | null) => value ? `v${value}` : '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, sorter: true, render: formatManagementDateTime },
  ];

  return <div className="data-source-detail-tab-panel">
    <div className="data-source-related-toolbar">
      <Form<ServiceFilters> autoComplete="off" form={form} layout="inline" onFinish={(values) => { setFilters(values); setPage(1); }}>
        <Form.Item name="keyword" label="名称"><Input allowClear placeholder="名称或编码" className="data-source-related-keyword" /></Form.Item>
        <Form.Item name="type" label="类型"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(dataServiceTypeLabels) as [DataServiceType, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" className="data-source-related-select" options={(Object.entries(dataServiceStatusLabels) as [DataServiceStatus, string][]).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="relationKind" label="关系"><Select allowClear placeholder="全部" className="data-source-related-select" options={Object.entries(relationKindLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      </Form>
      <Space size={4}>
        <Button type="primary" onClick={() => form.submit()}>查询</Button>
        <Button onClick={() => { form.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
        <Tooltip title="刷新关联服务"><Button icon={<ReloadOutlined />} aria-label="刷新关联服务" loading={query.isFetching} onClick={() => void query.refetch()} /></Tooltip>
      </Space>
    </div>
    {query.error && errorAlert('关联数据服务加载失败', () => void query.refetch())}
    <Table<DataSourceRelatedService>
      className="management-table" size="small" rowKey="serviceId" loading={query.isFetching}
      dataSource={query.data?.content ?? []} columns={columns} scroll={{ x: 1_320, y: '100%' }}
      pagination={{ current: page, pageSize, total: query.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
      onChange={(pagination, _filters, sorter) => {
        setPage(pagination.current ?? 1); setPageSize(pagination.pageSize ?? 20);
        if (!Array.isArray(sorter) && sorter.field) {
          const fields: Record<string, string> = { serviceName: 'name', serviceType: 'type', status: 'status', updatedAt: 'updatedAt' };
          setSort(`${sorter.order === 'descend' ? '-' : ''}${fields[String(sorter.field)] ?? 'updatedAt'},name`);
        }
      }}
    />
  </div>;
};
