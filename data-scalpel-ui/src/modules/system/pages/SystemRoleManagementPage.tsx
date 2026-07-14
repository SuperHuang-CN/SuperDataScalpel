import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { SystemRoleDrawer } from '../components/SystemRoleDrawer';
import { SystemRolePermissionsDrawer } from '../components/SystemRolePermissionsDrawer';
import { useCurrentUser, useDeleteSystemRole, useSystemPermissions, useSystemRoles } from '../hooks/useSystemAccess';
import { buildSystemRoleSearch } from '../model/systemAccessSearch';
import type { KeywordFilter, SystemRole } from '../model/systemAccess';

const DEFAULT_PAGE_SIZE = 20;

export const SystemRoleManagementPage = () => {
  const [filterForm] = Form.useForm<KeywordFilter>();
  const [filters, setFilters] = useState<KeywordFilter>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingRole, setEditingRole] = useState<SystemRole | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [permissionRole, setPermissionRole] = useState<SystemRole | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const currentUserQuery = useCurrentUser();
  const canManage = currentUserQuery.data?.permissions.includes('system.role.manage') ?? false;
  const canViewPermissions = currentUserQuery.data?.permissions.includes('system.permission.view') ?? false;
  const request = useMemo(() => ({
    search: buildSystemRoleSearch(filters), page, size, sort: '-builtIn,name,code',
  }), [filters, page, size]);
  const rolesQuery = useSystemRoles(request);
  const permissionsQuery = useSystemPermissions({ page: 0, size: 500, sort: 'module,sortOrder,code' }, canManage && canViewPermissions);
  const deleteMutation = useDeleteSystemRole();

  const search = (nextFilters: KeywordFilter) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const remove = async (role: SystemRole) => {
    try {
      await deleteMutation.mutateAsync(role.id);
      messageApi.success('角色已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除角色失败');
    }
  };

  const columns: TableProps<SystemRole>['columns'] = [
    { title: '角色名称', dataIndex: 'name', width: 200, ellipsis: true },
    { title: '角色编码', dataIndex: 'code', width: 190, render: (value: string) => <code>{value}</code> },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (value: string | null) => value || '—' },
    { title: '权限数', dataIndex: 'permissionIds', width: 100, render: (value: string[]) => value.length },
    { title: '类型', dataIndex: 'builtIn', width: 110, render: (value: boolean) => value ? <Tag color="gold">内置</Tag> : <Tag>自定义</Tag> },
    {
      title: '操作', key: 'actions', fixed: 'right', width: 132,
      render: (_: unknown, role: SystemRole) => canManage ? (
        <Space size={2}>
          <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${role.name}`} onClick={() => setEditingRole(role)} /></Tooltip>
          <Tooltip title={role.builtIn ? '内置角色的权限由系统维护' : '配置权限'}>
            <Button type="text" disabled={role.builtIn || !canViewPermissions} icon={<SafetyCertificateOutlined />} aria-label={`配置${role.name}权限`} onClick={() => setPermissionRole(role)} />
          </Tooltip>
          {!role.builtIn && (
            <Popconfirm title="删除角色" description={`确认删除“${role.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(role)}>
              <Tooltip title="删除"><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${role.name}`} /></Tooltip>
            </Popconfirm>
          )}
        </Space>
      ) : '—',
    },
  ];

  return (
    <>
      {messageContext}
      <Card className="management-card">
        <div className="management-toolbar">
          <Form<KeywordFilter> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
            <Form.Item name="keyword" label="名称/编码"><Input allowClear placeholder="按角色名称或编码筛选" className="data-source-keyword-input" /></Form.Item>
          </Form>
          <Space size={4} className="management-toolbar-actions">
            <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void rolesQuery.refetch()}>刷新</Button>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
          </Space>
        </div>
        <Table<SystemRole>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={rolesQuery.data?.content ?? []} loading={rolesQuery.isFetching} scroll={{ x: 1010, y: '100%' }}
          pagination={{
            current: page + 1, pageSize: size, total: rolesQuery.data?.totalElements ?? 0, size: 'small',
            position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
        />
      </Card>
      <SystemRoleDrawer open={createDrawerOpen || Boolean(editingRole)} role={editingRole} onClose={() => { setCreateDrawerOpen(false); setEditingRole(null); }} />
      <SystemRolePermissionsDrawer
        open={Boolean(permissionRole)} role={permissionRole}
        permissions={(permissionsQuery.data?.content ?? []).filter((permission) => permission.active)} loading={permissionsQuery.isFetching}
        onClose={() => setPermissionRole(null)}
      />
    </>
  );
};
