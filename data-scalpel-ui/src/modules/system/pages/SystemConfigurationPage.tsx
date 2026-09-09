import { EditOutlined, EllipsisOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Input, Table, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { ManagementCode, ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { PanoramaMapSettingsDrawer } from '../components/PanoramaMapSettingsDrawer';
import { panoramaMapSettingsSummary } from '../model/panoramaMapSettings';
import { SystemConfigurationDrawer } from '../components/SystemConfigurationDrawer';
import { useCurrentUser } from '../hooks/useSystemAccess';
import { useSystemConfigurations } from '../hooks/useSystemConfigurations';
import { buildSystemConfigurationSearch } from '../model/configurationSearch';
import type { SystemConfiguration, SystemConfigurationFilters } from '../model/systemConfiguration';

const DEFAULT_PAGE_SIZE = 20;

const valueTypeLabels: Record<SystemConfiguration['valueType'], string> = {
  STRING: '文本',
  INTEGER: '整数',
  BOOLEAN: '布尔值',
};

export const SystemConfigurationPage = () => {
  const [filterForm] = Form.useForm<SystemConfigurationFilters>();
  const [filters, setFilters] = useState<SystemConfigurationFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingConfiguration, setEditingConfiguration] = useState<SystemConfiguration | null>(null);
  const currentUserQuery = useCurrentUser();
  const canUpdate = currentUserQuery.data?.permissions.includes('system.configuration.update') ?? false;

  const request = useMemo(() => ({
    search: buildSystemConfigurationSearch(filters),
    page,
    size,
    sort: 'sortOrder,configKey',
  }), [filters, page, size]);
  const configurationsQuery = useSystemConfigurations(request);

  const columns: TableProps<SystemConfiguration>['columns'] = [
    {
      title: '配置项', dataIndex: 'name', width: 260,
      render: (value: string, configuration) => <ManagementListCell icon={<SettingOutlined />} iconTone="violet" primary={value} secondary={configuration.description || '—'} />,
    },
    { title: '配置键', dataIndex: 'configKey', width: 230, render: (value: string) => <ManagementCode value={value} /> },
    {
      title: '当前值',
      dataIndex: 'configValue',
      width: 230,
      render: (value: string, configuration) => <ManagementListCell primary={configuration.configKey === 'panorama.map' ? panoramaMapSettingsSummary(value) : value} secondary="系统配置值" />,
    },
    {
      title: '类型 / 排序', width: 130,
      render: (_value: unknown, configuration) => <ManagementListCell primary={valueTypeLabels[configuration.valueType]} secondary={`排序 ${configuration.sortOrder}`} />,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'action',
      width: 112,
      render: (_: unknown, configuration: SystemConfiguration) => canUpdate ? (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${configuration.name}`} onClick={() => setEditingConfiguration(configuration)} /></Tooltip>
          </div>
          <Dropdown menu={{ items: [{ key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => setEditingConfiguration(configuration) }] satisfies MenuProps['items'] }} trigger={['click']}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${configuration.name}的更多操作`} /></Tooltip>
          </Dropdown>
        </div>
      ) : '—',
    },
  ];

  const search = (nextFilters: SystemConfigurationFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  return (
    <>
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<SystemConfigurationFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
            <Form.Item name="name"><ManagementSearchInput allowClear placeholder="搜索配置名称" /></Form.Item>
            <Form.Item name="configKey"><Input allowClear placeholder="配置键，如 platform.name" /></Form.Item>
          </Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={configurationsQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <span className="management-result-title">配置列表 <span className="management-result-count">共 {configurationsQuery.data?.totalElements ?? 0} 项</span></span>
          <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新配置列表" onClick={() => void configurationsQuery.refetch()} /></Tooltip></div>
          </div>
          <Table<SystemConfiguration>
          size="small"
          className="management-table"
          rowKey="id"
          columns={columns}
          dataSource={configurationsQuery.data?.content ?? []}
          loading={configurationsQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: configurationsQuery.data?.totalElements ?? 0,
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
      {editingConfiguration?.configKey === 'panorama.map' ? <PanoramaMapSettingsDrawer configuration={editingConfiguration} onClose={() => setEditingConfiguration(null)} /> : <SystemConfigurationDrawer
        open={Boolean(editingConfiguration)}
        configuration={editingConfiguration}
        onClose={() => setEditingConfiguration(null)}
      />}
    </>
  );
};
