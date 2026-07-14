import { ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Card, Form, Input, Space, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useSystemPermissions } from '../hooks/useSystemAccess';
import { groupSystemPermissions } from '../model/systemPermissionGrouping';
import { buildSystemPermissionSearch } from '../model/systemAccessSearch';
import type { KeywordFilter, SystemPermission } from '../model/systemAccess';

const MAXIMUM_PERMISSION_PAGE_SIZE = 500;

interface PermissionGroupRow {
  id: string;
  kind: 'group';
  module: string;
  permissionCount: number;
  children: PermissionItemRow[];
}

type PermissionItemRow = SystemPermission & { kind: 'permission' };
type PermissionTableRow = PermissionGroupRow | PermissionItemRow;

export const SystemPermissionManagementPage = () => {
  const [filterForm] = Form.useForm<KeywordFilter>();
  const [filters, setFilters] = useState<KeywordFilter>({});
  const request = useMemo(() => ({
    search: buildSystemPermissionSearch(filters), page: 0, size: MAXIMUM_PERMISSION_PAGE_SIZE, sort: 'module,sortOrder,code',
  }), [filters]);
  const permissionsQuery = useSystemPermissions(request);
  const permissions = useMemo(() => permissionsQuery.data?.content ?? [], [permissionsQuery.data]);
  const tableData = useMemo<PermissionGroupRow[]>(() => groupSystemPermissions(permissions).map((group) => ({
    id: `module:${group.module}`,
    kind: 'group',
    module: group.module,
    permissionCount: group.permissions.length,
    children: group.permissions.map((permission) => ({ ...permission, kind: 'permission' })),
  })), [permissions]);

  const search = (nextFilters: KeywordFilter) => {
    setFilters(nextFilters);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const columns: TableProps<PermissionTableRow>['columns'] = [
    {
      title: '权限名称', dataIndex: 'name', width: 240,
      render: (_: unknown, record) => record.kind === 'group' ? (
        <Space size={8}>
          <Typography.Text strong>{record.module}</Typography.Text>
          <Tag>{record.permissionCount} 项</Tag>
        </Space>
      ) : record.name,
    },
    {
      title: '权限编码', dataIndex: 'code', width: 300,
      render: (value: string | undefined, record) => record.kind === 'group' ? null : <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: '说明', dataIndex: 'description', ellipsis: true,
      render: (value: string | null | undefined, record) => record.kind === 'group' ? null : value || '—',
    },
    {
      title: '状态', dataIndex: 'active', width: 100,
      render: (value: boolean | undefined, record) => record.kind === 'group' ? null : <Tag color={value ? 'success' : 'default'}>{value ? '有效' : '已停用'}</Tag>,
    },
  ];

  return (
    <Card className="management-card">
      <div className="management-toolbar">
        <Form<KeywordFilter> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
          <Form.Item name="keyword" label="关键字"><Input allowClear placeholder="按模块、名称或权限编码筛选" className="data-source-keyword-input" /></Form.Item>
        </Form>
        <Space size={4} className="management-toolbar-actions">
          <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
          <Button onClick={reset}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={() => void permissionsQuery.refetch()}>刷新</Button>
        </Space>
      </div>
      <Table<PermissionTableRow>
        size="small" className="management-table" rowKey="id" columns={columns}
        dataSource={tableData} loading={permissionsQuery.isFetching} scroll={{ x: 900, y: '100%' }}
        pagination={false}
        expandable={{ defaultExpandAllRows: true, expandRowByClick: true, rowExpandable: (record) => record.kind === 'group' }}
        rowClassName={(record) => record.kind === 'group' ? 'permission-group-row' : ''}
      />
      <div className="permission-group-summary">
        {permissions.length === (permissionsQuery.data?.totalElements ?? 0)
          ? `共 ${permissions.length} 项 · ${tableData.length} 个模块`
          : `已展示 ${permissions.length} / ${permissionsQuery.data?.totalElements ?? 0} 项 · ${tableData.length} 个模块`}
      </div>
    </Card>
  );
};
