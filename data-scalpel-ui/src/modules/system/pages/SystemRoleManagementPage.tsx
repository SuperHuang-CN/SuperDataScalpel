import { DeleteOutlined, EditOutlined, EllipsisOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, TeamOutlined } from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Table, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ManagementCode, ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
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
    { title: '角色', dataIndex: 'name', width: 260, render: (value: string, role) => <ManagementListCell icon={<TeamOutlined />} iconTone="violet" primary={value} secondary={<ManagementCode value={role.code} />} /> },
    { title: '说明', dataIndex: 'description', width: 280, render: (value: string | null) => <ManagementListCell primary={value || '—'} secondary="角色说明" /> },
    { title: '权限 / 类型', width: 150, render: (_value: unknown, role) => <ManagementListCell primary={`${role.permissionIds.length} 项权限`} secondary={role.builtIn ? '内置角色' : '自定义角色'} /> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作', key: 'actions', width: 112,
      render: (_: unknown, role: SystemRole) => canManage ? (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${role.name}`} onClick={() => setEditingRole(role)} /></Tooltip>
            <Tooltip title={role.builtIn ? '内置角色的权限由系统维护' : '配置权限'}><Button type="text" disabled={role.builtIn || !canViewPermissions} icon={<SafetyCertificateOutlined />} aria-label={`配置${role.name}权限`} onClick={() => setPermissionRole(role)} /></Tooltip>
          </div>
          <Dropdown menu={{ items: [
            { key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => setEditingRole(role) },
            { key: 'permissions', icon: <SafetyCertificateOutlined />, label: role.builtIn ? '权限由系统维护' : '配置权限', disabled: role.builtIn || !canViewPermissions, onClick: () => setPermissionRole(role) },
            ...(!role.builtIn ? [{ type: 'divider' as const }, { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '删除角色', content: `确认删除“${role.name}”吗？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => remove(role) }) }] : []),
          ] satisfies MenuProps['items'] }} trigger={['click']}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${role.name}的更多操作`} /></Tooltip>
          </Dropdown>
        </div>
      ) : '—',
    },
  ];

  return (
    <>
      {messageContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<KeywordFilter> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}><Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索角色名称或编码" className="data-source-keyword-input" /></Form.Item></Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={rolesQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <span className="management-result-title">角色列表 <span className="management-result-count">共 {rolesQuery.data?.totalElements ?? 0} 项</span></span>
          <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新角色列表" onClick={() => void rolesQuery.refetch()} /></Tooltip>{canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}</div>
          </div>
          <Table<SystemRole>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={rolesQuery.data?.content ?? []} loading={rolesQuery.isFetching} scroll={{ y: '100%' }}
          pagination={{
            current: page + 1, pageSize: size, total: rolesQuery.data?.totalElements ?? 0, size: 'small',
            position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
          />
        </div>
      </section>
      <SystemRoleDrawer open={createDrawerOpen || Boolean(editingRole)} role={editingRole} onClose={() => { setCreateDrawerOpen(false); setEditingRole(null); }} />
      <SystemRolePermissionsDrawer
        open={Boolean(permissionRole)} role={permissionRole}
        permissions={(permissionsQuery.data?.content ?? []).filter((permission) => permission.active)} loading={permissionsQuery.isFetching}
        onClose={() => setPermissionRole(null)}
      />
    </>
  );
};
