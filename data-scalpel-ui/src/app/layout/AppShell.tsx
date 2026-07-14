import {
  ApiOutlined,
  ApartmentOutlined,
  ClusterOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { BreadcrumbProps, MenuProps } from 'antd';
import { Breadcrumb, Button, Layout, Menu, Space, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useCurrentUser, useLogout, useSystemConfigurations } from '../../modules/system';

const navigationItems = (permissions: Set<string>): NonNullable<MenuProps['items']> => {
  const systemItems = [
    permissions.has('system.user.view') ? { key: '/system/users', label: '用户管理' } : null,
    permissions.has('system.role.view') ? { key: '/system/roles', label: '角色管理' } : null,
    permissions.has('system.permission.view') ? { key: '/system/permissions', label: '权限管理' } : null,
    permissions.has('system.configuration.view') ? { key: '/system/configurations', label: '系统配置' } : null,
  ].filter((item): item is { key: string; label: string } => item !== null);

  return [
    { key: '/', icon: <DashboardOutlined />, label: '工作台' },
    ...(systemItems.length ? [{ key: 'system', icon: <SettingOutlined />, label: '系统管理', children: systemItems }] : []),
    ...(permissions.has('datasource.view') ? [{ key: '/datasource', icon: <DatabaseOutlined />, label: '数据源管理' }] : []),
    ...(permissions.has('model.view') ? [{ key: '/model', icon: <ClusterOutlined />, label: '模型管理' }] : []),
    {
      key: 'task',
      icon: <ApartmentOutlined />,
      label: '任务管理',
      children: [
        { key: '/task', label: '任务列表' },
        { key: '/task/orchestration', label: '任务编排' },
      ],
    },
    { key: '/dataservice', icon: <ApiOutlined />, label: '数据服务' },
  ];
};

const selectedMenuKey = (pathname: string) => {
  if (pathname.startsWith('/task/orchestration')) return '/task/orchestration';
  if (pathname.startsWith('/task')) return '/task';
  if (pathname.startsWith('/system/users')) return '/system/users';
  if (pathname.startsWith('/system/roles')) return '/system/roles';
  if (pathname.startsWith('/system/permissions')) return '/system/permissions';
  if (pathname.startsWith('/system/configurations')) return '/system/configurations';
  if (pathname.startsWith('/system')) return 'system';
  if (pathname.startsWith('/datasource')) return '/datasource';
  if (pathname.startsWith('/model')) return '/model';
  if (pathname.startsWith('/dataservice')) return '/dataservice';
  return '/';
};

const breadcrumbItems = (pathname: string): BreadcrumbProps['items'] => {
  if (pathname.startsWith('/system/users')) return [{ title: '系统管理' }, { title: '用户管理' }];
  if (pathname.startsWith('/system/roles')) return [{ title: '系统管理' }, { title: '角色管理' }];
  if (pathname.startsWith('/system/permissions')) return [{ title: '系统管理' }, { title: '权限管理' }];
  if (pathname.startsWith('/system/configurations')) return [{ title: '系统管理' }, { title: '系统配置' }];
  if (pathname.startsWith('/system')) return [{ title: '系统管理' }];
  if (pathname.startsWith('/datasource')) return [{ title: '数据源管理' }];
  if (pathname.startsWith('/model')) return [{ title: '模型管理' }];
  if (pathname.startsWith('/task/orchestration')) return [{ title: '任务管理' }, { title: '任务编排' }];
  if (pathname.startsWith('/task')) return [{ title: '任务管理' }, { title: '任务列表' }];
  if (pathname.startsWith('/dataservice')) return [{ title: '数据服务' }];
  return [{ title: '工作台' }];
};

export const AppShell = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const currentUserQuery = useCurrentUser();
  const logout = useLogout();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewConfigurations = permissions.has('system.configuration.view');
  const configurationsQuery = useSystemConfigurations({ page: 0, size: 20, sort: 'sortOrder,configKey' }, canViewConfigurations);
  const platformName = configurationsQuery.data?.content.find((item) => item.configKey === 'platform.name')?.configValue
    ?? 'DataScalpel';
  const platformSubtitle = configurationsQuery.data?.content.find((item) => item.configKey === 'platform.subtitle')?.configValue
    ?? '内网部署 · 模块化单体';

  return (
    <Layout className="app-shell">
      <Layout.Sider breakpoint="lg" collapsedWidth="0" className="app-sidebar">
        <div className="app-brand">
          <DatabaseOutlined />
          <span>{platformName}</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedMenuKey(location.pathname)]}
          defaultOpenKeys={['system', 'task']}
          items={navigationItems(permissions)}
          onClick={({ key }) => navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Breadcrumb items={breadcrumbItems(location.pathname)} />
          <Space size={12}>
            <Typography.Text type="secondary">{platformSubtitle}</Typography.Text>
            <Typography.Text>{currentUserQuery.data?.username}</Typography.Text>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={() => {
                logout();
                navigate('/login', { replace: true });
              }}
            >
              退出
            </Button>
          </Space>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
};
