import { DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
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
    { title: '用户名', dataIndex: 'username', width: 180, render: (value: string) => <code>{value}</code> },
    { title: '显示名称', dataIndex: 'displayName', width: 180, ellipsis: true },
    { title: '角色', dataIndex: 'roleName', width: 180, ellipsis: true },
    {
      title: '状态', dataIndex: 'enabled', width: 96,
      render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false }) },
    {
      title: '操作', key: 'actions', fixed: 'right', width: 120,
      render: (_: unknown, user: SystemUser) => canManage ? (
        <Space size={2}>
          <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${user.displayName}`} onClick={() => setEditingUser(user)} /></Tooltip>
          <Tooltip title="重置密码"><Button type="text" icon={<KeyOutlined />} aria-label={`重置${user.displayName}密码`} onClick={() => setPasswordUser(user)} /></Tooltip>
          <Popconfirm title="删除用户" description={`确认删除“${user.displayName}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(user)}>
            <Tooltip title="删除"><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${user.displayName}`} /></Tooltip>
          </Popconfirm>
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
            <Form.Item name="keyword" label="用户名/名称"><Input allowClear placeholder="按用户名或显示名称筛选" className="data-source-keyword-input" /></Form.Item>
          </Form>
          <Space size={4} className="management-toolbar-actions">
            <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void usersQuery.refetch()}>刷新</Button>
            {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
          </Space>
        </div>
        <Table<SystemUser>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={usersQuery.data?.content ?? []} loading={usersQuery.isFetching} scroll={{ x: 936, y: '100%' }}
          pagination={{
            current: page + 1, pageSize: size, total: usersQuery.data?.totalElements ?? 0, size: 'small',
            position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE); }}
        />
      </Card>
      <SystemUserDrawer
        open={createDrawerOpen || Boolean(editingUser)} user={editingUser} roles={rolesQuery.data?.content ?? []}
        onClose={() => { setCreateDrawerOpen(false); setEditingUser(null); }}
      />
      <SystemUserPasswordModal user={passwordUser} onClose={() => setPasswordUser(null)} />
    </>
  );
};
