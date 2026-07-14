import { EditOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Card, Form, Input, Space, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
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
    { title: '名称', dataIndex: 'name', width: 160 },
    { title: '配置键', dataIndex: 'configKey', width: 220, render: (value: string) => <Typography.Text code>{value}</Typography.Text> },
    {
      title: '当前值',
      dataIndex: 'configValue',
      render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text>,
    },
    {
      title: '类型',
      dataIndex: 'valueType',
      width: 100,
      render: (value: SystemConfiguration['valueType']) => <Tag>{valueTypeLabels[value]}</Tag>,
    },
    { title: '说明', dataIndex: 'description', width: 280, render: (value: string | null) => value || '—' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, configuration: SystemConfiguration) => canUpdate ? (
        <Button type="link" icon={<EditOutlined />} onClick={() => setEditingConfiguration(configuration)}>
          修改
        </Button>
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
      <Card className="management-card">
        <div className="management-toolbar">
          <Form<SystemConfigurationFilters>
            form={filterForm}
            layout="inline"
            className="management-filter-form"
            onFinish={search}
          >
            <Form.Item name="name" label="名称">
              <Input allowClear placeholder="按名称筛选" />
            </Form.Item>
            <Form.Item name="configKey" label="配置键">
              <Input allowClear placeholder="如 platform.name" />
            </Form.Item>
          </Form>
          <Space size={4} className="management-toolbar-actions">
            <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void configurationsQuery.refetch()}>
              刷新
            </Button>
          </Space>
        </div>
        <Table<SystemConfiguration>
          size="small"
          className="management-table"
          rowKey="id"
          columns={columns}
          dataSource={configurationsQuery.data?.content ?? []}
          loading={configurationsQuery.isFetching}
          scroll={{ x: 1040, y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: configurationsQuery.data?.totalElements ?? 0,
            size: 'small',
            position: ['bottomRight'],
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => {
            setPage((pagination.current ?? 1) - 1);
            setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
          }}
        />
      </Card>
      <SystemConfigurationDrawer
        open={Boolean(editingConfiguration)}
        configuration={editingConfiguration}
        onClose={() => setEditingConfiguration(null)}
      />
    </>
  );
};
