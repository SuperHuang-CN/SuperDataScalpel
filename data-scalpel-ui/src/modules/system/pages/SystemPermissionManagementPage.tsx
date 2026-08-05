import { ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Form, Space, Table, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { ManagementCode, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
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
      title: '权限', dataIndex: 'name', width: 360,
      render: (_: unknown, record) => record.kind === 'group' ? (
        <Space size={8}>
          <Typography.Text strong>{record.module}</Typography.Text>
          <Typography.Text type="secondary">{record.permissionCount} 项</Typography.Text>
        </Space>
      ) : <ManagementListCell icon={<SafetyCertificateOutlined />} iconTone="cyan" primary={record.name} secondary={<ManagementCode value={record.code} />} />,
    },
    {
      title: '说明', dataIndex: 'description',
      render: (value: string | null | undefined, record) => record.kind === 'group' ? null : <ManagementListCell primary={value || '—'} secondary="权限用途说明" />,
    },
    {
      title: '状态 / 更新时间', width: 180,
      render: (_value: unknown, record) => record.kind === 'group' ? null : <ManagementListCell primary={<ManagementStatusIndicator label={record.active ? '有效' : '已停用'} tone={record.active ? 'success' : 'default'} />} secondary={formatManagementDateTime(record.updatedAt)} />,
    },
  ];

  return (
    <section className="management-workbench">
      <div className="management-filter-strip">
        <Form<KeywordFilter> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}><Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索模块、名称或权限编码" className="data-source-keyword-input" /></Form.Item></Form>
        <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={permissionsQuery.isFetching} onReset={reset} />
      </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
        <span className="management-result-title">权限列表 <span className="management-result-count">共 {permissionsQuery.data?.totalElements ?? 0} 项</span></span>
        <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新权限列表" onClick={() => void permissionsQuery.refetch()} /></Tooltip></div>
        </div>
        <Table<PermissionTableRow>
        size="small" className="management-table" rowKey="id" columns={columns}
        dataSource={tableData} loading={permissionsQuery.isFetching} scroll={{ y: '100%' }}
        pagination={false}
        expandable={{ defaultExpandAllRows: true, expandRowByClick: true, rowExpandable: (record) => record.kind === 'group' }}
        rowClassName={(record) => record.kind === 'group' ? 'permission-group-row' : ''}
        />
        <div className="permission-group-summary">
          {permissions.length === (permissionsQuery.data?.totalElements ?? 0)
            ? `共 ${permissions.length} 项 · ${tableData.length} 个模块`
            : `已展示 ${permissions.length} / ${permissionsQuery.data?.totalElements ?? 0} 项 · ${tableData.length} 个模块`}
        </div>
      </div>
    </section>
  );
};
