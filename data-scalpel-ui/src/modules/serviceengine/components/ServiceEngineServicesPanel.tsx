import { ApiOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Form, Select, Space, Table, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { buildDataServiceSearch, dataServiceDeploymentStatusLabels, dataServiceStatusLabels, dataServiceTypeLabels, useDataServices } from '../../dataservice';
import type {
  DataServiceDeploymentStatus,
  DataServiceFilters,
  DataServiceStatus,
  DataServiceSummary,
} from '../../dataservice';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';

const DEFAULT_PAGE_SIZE = 20;

interface ServiceEngineServicesPanelProps {
  engineId: string;
}

const serviceStatusTones: Record<DataServiceStatus, ManagementStatusTone> = {
  DRAFT: 'default',
  ENABLED: 'success',
  DISABLED: 'warning',
};

const deploymentStatusTones: Record<DataServiceDeploymentStatus, ManagementStatusTone> = {
  PENDING: 'processing',
  DEPLOYED: 'success',
  FAILED: 'error',
  REMOVING: 'processing',
  REMOVED: 'default',
};

const typeOptions = Object.entries(dataServiceTypeLabels).map(([value, label]) => ({ value, label }));
const statusOptions = Object.entries(dataServiceStatusLabels).map(([value, label]) => ({ value, label }));

export const ServiceEngineServicesPanel = ({ engineId }: ServiceEngineServicesPanelProps) => {
  const [filterForm] = Form.useForm<DataServiceFilters>();
  const [filters, setFilters] = useState<DataServiceFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const navigate = useNavigate();
  const location = useLocation();
  const request = useMemo(() => ({
    search: buildDataServiceSearch({ ...filters, engineId }),
    page,
    size,
    sort: '-updatedAt,code',
  }), [engineId, filters, page, size]);
  const servicesQuery = useDataServices(request);

  const search = (nextFilters: DataServiceFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const openService = (service: DataServiceSummary) => navigate(`/dataservice/${service.id}`, {
    state: {
      returnTo: `${location.pathname}?tab=services`,
      returnLabel: '返回引擎',
    },
  });

  const columns: TableProps<DataServiceSummary>['columns'] = [
    {
      title: '服务',
      dataIndex: 'name',
      width: 260,
      render: (value: string, service) => (
        <ManagementListCell
          icon={<ApiOutlined />}
          iconTone="cyan"
          primary={<Button type="link" size="small" className="data-service-name-button" onClick={() => openService(service)}>{value}</Button>}
          secondary={<ManagementCode value={service.code} />}
        />
      ),
    },
    {
      title: '类型 / 来源',
      width: 220,
      render: (_: unknown, service) => (
        <ManagementListCell
          primary={dataServiceTypeLabels[service.type]}
          secondary={service.sourceName || '尚未配置来源'}
        />
      ),
    },
    {
      title: 'Engine 路由',
      dataIndex: 'engineRoutePath',
      width: 230,
      render: (value: string) => <ManagementCode value={value} />,
    },
    {
      title: '服务状态',
      width: 120,
      render: (_: unknown, service) => (
        <ManagementStatusIndicator label={dataServiceStatusLabels[service.status]} tone={serviceStatusTones[service.status]} />
      ),
    },
    {
      title: '部署状态 / 错误',
      width: 220,
      render: (_: unknown, service) => (
        <ManagementListCell
          primary={service.deploymentStatus
            ? <ManagementStatusIndicator label={dataServiceDeploymentStatusLabels[service.deploymentStatus]} tone={deploymentStatusTones[service.deploymentStatus]} title={service.deploymentError || undefined} />
            : <ManagementStatusIndicator label="未部署" />}
          secondary={service.deploymentError || '—'}
        />
      ),
    },
    {
      title: '部署 / 更新时间',
      width: 180,
      render: (_: unknown, service) => (
        <ManagementListCell
          primary={service.deployedAt ? `部署 ${new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(service.deployedAt))}` : '尚未部署'}
          secondary={<ManagementDateTime value={service.updatedAt} />}
        />
      ),
    },
  ];

  return (
    <section className="service-engine-tab-panel service-engine-services-panel">
      <div className="management-filter-strip">
        <Form<DataServiceFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索服务名称或编码" className="data-source-keyword-input" /></Form.Item>
          <Form.Item name="type"><Select allowClear placeholder="全部类型" className="data-source-filter-select" options={typeOptions} /></Form.Item>
          <Form.Item name="status"><Select allowClear placeholder="全部状态" className="data-source-filter-select" options={statusOptions} /></Form.Item>
        </Form>
        <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={servicesQuery.isFetching} onReset={reset} />
      </div>
      <div className="management-results-surface service-engine-tab-results">
        <div className="management-result-toolbar">
          <div className="management-result-title">数据服务 <span className="management-result-count">共 {servicesQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新服务列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新引擎数据服务" onClick={() => void servicesQuery.refetch()} /></Tooltip>
          </Space>
        </div>
        <Table<DataServiceSummary>
          className="management-table"
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={servicesQuery.data?.content ?? []}
          loading={servicesQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: servicesQuery.data?.totalElements ?? 0,
            size: 'small',
            position: ['bottomRight'],
            hideOnSinglePage: false,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => {
            setPage((pagination.current ?? 1) - 1);
            setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
          }}
        />
      </div>
    </section>
  );
};
