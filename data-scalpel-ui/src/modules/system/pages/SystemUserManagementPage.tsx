import { DeleteOutlined, EditOutlined, EllipsisOutlined, KeyOutlined, PlusOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Table, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ApiError } from '../../../shared/api/http';
import { SystemUserDrawer } from '../components/SystemUserDrawer';
import { SystemUserPasswordModal } from '../components/SystemUserPasswordModal';
import { useCurrentUser, useDeleteSystemUser, useSystemRoles, useSystemUsers } from '../hooks/useSystemAccess';
import { buildSystemUserSearch } from '../model/systemAccessSearch';
import type { KeywordFilter, SystemUser } from '../model/systemAccess';

const DEFAULT_PAGE_SIZE = 20;

export const SystemUserManagementPage = () => {
  const [filterForm] = Form.useForm<KeywordFilter>();
  const [filters, setFilters] = useState<KeywordFilter>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingUser, setEditingUser] = useState<SystemUser | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [passwordUser, setPasswordUser] = useState<SystemUser | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const currentUserQuery = useCurrentUser();
  const canManage = currentUserQuery.data?.permissions.includes('system.user.manage') ?? false;
  const request = useMemo(() => ({
    search: buildSystemUserSearch(filters), page, size, sort: '-updatedAt,username',
  }), [filters, page, size]);
  const usersQuery = useSystemUsers(request);
  const rolesQuery = useSystemRoles({ page: 0, size: 500, sort: 'name,code' }, canManage);
  const deleteMutation = useDeleteSystemUser();

  const search = (nextFilters: KeywordFilter) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const remove = async (user: SystemUser) => {
    try {
      await deleteMutation.mutateAsync(user.id);
      messageApi.success('用户已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除用户失败');
    }
  };

  const columns: TableProps<SystemUser>['columns'] = [
    { title: '用户', dataIndex: 'displayName', width: 250, render: (value: string, user) => <ManagementListCell icon={<UserOutlined />} primary={value} secondary={<ManagementCode value={user.username} />} /> },
    { title: '角色', dataIndex: 'roleName', width: 220, render: (value: string) => <ManagementListCell primary={value} secondary="角色权限决定可用功能" /> },
    {
      title: '状态', dataIndex: 'enabled', width: 96,
      render: (value: boolean) => <ManagementStatusIndicator label={value ? '启用' : '停用'} tone={value ? 'success' : 'default'} />,
    },
    { title: '时间', width: 170, render: (_value: unknown, user) => <ManagementDateTime value={user.updatedAt} /> },
    {
      title: '操作', key: 'actions', width: 112,
      render: (_: unknown, user: SystemUser) => canManage ? (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${user.displayName}`} onClick={() => setEditingUser(user)} /></Tooltip>
            <Tooltip title="重置密码"><Button type="text" icon={<KeyOutlined />} aria-label={`重置${user.displayName}密码`} onClick={() => setPasswordUser(user)} /></Tooltip>
          </div>
          <Dropdown menu={{ items: [
            { key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => setEditingUser(user) },
            { key: 'password', icon: <KeyOutlined />, label: '重置密码', onClick: () => setPasswordUser(user) },
            { type: 'divider' },
            { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '删除用户', content: `确认删除“${user.displayName}”吗？`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => remove(user) }) },
          ] satisfies MenuProps['items'] }} trigger={['click']}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${user.displayName}的更多操作`} /></Tooltip>
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
          <Form<KeywordFilter> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
            <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索用户名或显示名称" className="data-source-keyword-input" /></Form.Item>
          </Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={usersQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <span className="management-result-title">用户列表 <span className="management-result-count">共 {usersQuery.data?.totalElements ?? 0} 项</span></span>
          <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新用户列表" onClick={() => void usersQuery.refetch()} /></Tooltip>{canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}</div>
          </div>
          <Table<SystemUser>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={usersQuery.data?.content ?? []} loading={usersQuery.isFetching} scroll={{ y: '100%' }}
          pagination={{
            current: page + 1, pageSize: size, total: usersQuery.data?.totalElements ?? 0, size: 'small',
            position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
          />
        </div>
      </section>
      <SystemUserDrawer
        open={createDrawerOpen || Boolean(editingUser)} user={editingUser} roles={rolesQuery.data?.content ?? []}
        onClose={() => { setCreateDrawerOpen(false); setEditingUser(null); }}
      />
      <SystemUserPasswordModal user={passwordUser} onClose={() => setPasswordUser(null)} />
    </>
  );
};
