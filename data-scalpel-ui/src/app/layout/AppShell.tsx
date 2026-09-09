import {
  ApiOutlined,
  ApartmentOutlined,
  CompassOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExportOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RobotOutlined,
  ToolOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { BreadcrumbProps, MenuProps } from 'antd';
import { Breadcrumb, Button, Layout, Menu, Space, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AssistantDrawer, assistantPageKeyForPath, assistantPagePath } from '../../modules/assistant';
import { useCurrentUser, useLogout, useSystemConfigurations } from '../../modules/system';
import { getTaskView, resolveTaskView, taskIdFromPath, taskViews, useTask, type TaskViewConfiguration } from '../../modules/task';
import { NotificationBell } from '../../modules/operations';

const APP_SIDEBAR_COLLAPSED_STORAGE_KEY = 'data-scalpel.ui.app-sidebar.collapsed';

const TOP_LEVEL_MANAGEMENT_PATHS = new Set([
  '/operations',
  '/operations/alerts',
  '/operations/configuration',
  '/system/configurations',
  '/system/model-warehouse-layers',
  '/system/ai-models',
  '/system/users',
  '/system/roles',
  '/system/permissions',
  '/asset-management/assets',
  '/asset-management/domains',
  '/datasource',
  '/file-dataset',
  '/panorama',
  '/standard/dictionaries',
  '/model',
  '/metrics',
  '/model/field-templates',
  '/data-entry',
  ...taskViews.map(view => view.path),
  '/task/masking-rules',
  '/service-engine',
  '/compute-engine',
  '/dataservice',
  '/dataservice/consumers',
  '/dataservice/operations',
  '/mcp-management',
  '/mcp-management/access-tokens',
  '/mcp-management/invocations',
]);

const isBusinessDetailPath = (pathname: string) => (
  /^\/metrics\/[^/]+$/.test(pathname)
  || /^\/datasource\/[^/]+$/.test(pathname)
  || /^\/file-dataset\/[^/]+$/.test(pathname)
  || /^\/panorama\/[^/]+$/.test(pathname)
  || /^\/standard\/dictionaries\/[^/]+$/.test(pathname)
  || /^\/model\/[^/]+$/.test(pathname)
  || /^\/data-entry\/[^/]+$/.test(pathname)
  || /^\/task\/[^/]+$/.test(pathname)
  || /^\/service-engine\/[^/]+$/.test(pathname)
  || /^\/compute-engine\/[^/]+$/.test(pathname)
  || /^\/dataservice\/[^/]+$/.test(pathname)
  || (/^\/mcp-management\/[^/]+$/.test(pathname) && pathname !== '/mcp-management/access-tokens' && pathname !== '/mcp-management/invocations')
);

const isDataServiceEditorPath = (pathname: string) => (
  /^\/dataservice\/[^/]+\/(edit|definition\/edit)$/.test(pathname)
);

const isTaskDefinitionEditorPath = (pathname: string) => (
  /^\/task\/[^/]+\/(definition|online-code)$/.test(pathname)
);

const isMcpToolEditorPath = (pathname: string) => (
  /^\/mcp-management\/[^/]+\/tools\/[^/]+\/edit$/.test(pathname)
);

const readSidebarCollapsedPreference = (): boolean => {
  try {
    return window.localStorage.getItem(APP_SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
};

const writeSidebarCollapsedPreference = (collapsed: boolean) => {
  try {
    window.localStorage.setItem(APP_SIDEBAR_COLLAPSED_STORAGE_KEY, String(collapsed));
  } catch {
    // Local storage may be unavailable in restricted browser environments.
  }
};

const navigationItems = (permissions: Set<string>): NonNullable<MenuProps['items']> => {
  const dataItems = [
    permissions.has('datasource.view') ? { key: '/datasource', label: '数据源' } : null,
    permissions.has('panorama.view') ? { key: '/panorama', label: '全景影像' } : null,
    permissions.has('filedataset.view') ? { key: '/file-dataset', label: '文件数据集' } : null,
    permissions.has('standard.dictionary.view') ? { key: '/standard/dictionaries', label: '码表管理' } : null,
    permissions.has('model.view') ? { key: '/model', label: '模型列表' } : null,
    permissions.has('metric.view') ? { key: '/metrics', label: '指标管理' } : null,
    permissions.has('model.view') ? { key: '/model/field-templates', label: '常用字段模板' } : null,
    permissions.has('dataentry.view') ? { key: '/data-entry', label: '数据填报' } : null,
  ].filter((item): item is { key: string; label: string } => item !== null);

  const runtimeItems = [
    permissions.has('service.engine.view') ? { key: '/service-engine', label: '服务引擎' } : null,
    permissions.has('compute.engine.view') ? { key: '/compute-engine', label: '计算引擎' } : null,
  ].filter((item): item is { key: string; label: string } => item !== null);

  const systemItems = [
    permissions.has('system.mcp.view') ? { key: '/system/system-mcp', label: '系统 MCP' } : null,
    permissions.has('system.user.view') ? { key: '/system/users', label: '用户管理' } : null,
    permissions.has('system.role.view') ? { key: '/system/roles', label: '角色管理' } : null,
    permissions.has('system.permission.view') ? { key: '/system/permissions', label: '权限管理' } : null,
    permissions.has('system.configuration.view') ? { key: '/system/configurations', label: '系统配置' } : null,
    permissions.has('system.configuration.view') ? { key: '/system/model-warehouse-layers', label: '数仓分层' } : null,
    permissions.has('system.configuration.view') ? { key: '/system/ai-models', label: 'AI 模型' } : null,
  ].filter((item): item is { key: string; label: string } => item !== null);

  return [
    { key: '/', icon: <DashboardOutlined />, label: '工作台' },
    {
      key: 'asset',
      icon: <CompassOutlined />,
      label: '数据资产',
      children: [
        { key: '/assets', label: <span>资产门户 <ExportOutlined aria-hidden /></span> },
        ...(permissions.has('asset.view') ? [{ key: '/asset-management/assets', label: '资产管理' }] : []),
        ...(permissions.has('directory.view') ? [{ key: '/asset-management/domains', label: '业务领域' }] : []),
      ],
    },
    ...(dataItems.length ? [{ key: 'data', icon: <DatabaseOutlined />, label: '数据管理', children: dataItems }] : []),
    ...(permissions.has('task.view') ? [{
      key: 'task',
      icon: <ApartmentOutlined />,
      label: '任务中心',
      children: [
        ...taskViews.filter(view => view.id !== 'all').map(view => ({ key: view.path, label: view.label })),
        { key: '/task/masking-rules', label: '脱敏规则' },
      ],
    }] : []),
    ...(permissions.has('service.view') ? [{
      key: 'dataservice',
      icon: <ApiOutlined />,
      label: '数据服务',
      children: [
        { key: '/dataservice', label: '服务列表' },
        { key: '/dataservice/consumers', label: '消费者管理' },
        { key: '/dataservice/operations', label: '调用统计' },
      ],
    }] : []),
    ...(permissions.has('mcp.view') || permissions.has('mcp.token.manage') ? [{
      key: 'mcp',
      icon: <ToolOutlined />,
      label: 'MCP 管理',
      children: [
        ...(permissions.has('mcp.view') ? [{ key: '/mcp-management', label: 'MCP Server' }] : []),
        ...(permissions.has('mcp.token.manage') ? [{ key: '/mcp-management/access-tokens', label: '访问凭证' }] : []),
        ...(permissions.has('mcp.view') ? [{ key: '/mcp-management/invocations', label: '调用日志' }] : []),
      ],
    }] : []),
    ...(runtimeItems.length ? [{ key: 'runtime', icon: <DeploymentUnitOutlined />, label: '运行管理', children: runtimeItems }] : []),
    ...(permissions.has('task.view') || permissions.has('compute.engine.view') || permissions.has('alert.manage') ? [{
      key: 'operations', icon: <DashboardOutlined />, label: '运行中心', children: [
        { key: '/operations', label: '运行工作台' },
        ...(permissions.has('task.view') || permissions.has('compute.engine.view') ? [{ key: '/operations/alerts', label: '告警中心' }] : []),
        ...(permissions.has('alert.manage') ? [{ key: '/operations/configuration', label: '告警配置' }] : []),
      ],
    }] : []),
    ...(systemItems.length ? [
      { type: 'divider' as const },
      { key: 'system', icon: <SettingOutlined />, label: '系统管理', children: systemItems },
    ] : []),
  ];
};

const menuGroupKey = (pathname: string): string | undefined => {
  if (pathname.startsWith('/operations')) return 'operations';
  if (pathname.startsWith('/asset-management')) return 'asset';
  if (pathname.startsWith('/datasource')
    || pathname.startsWith('/file-dataset')
    || pathname.startsWith('/panorama')
    || pathname.startsWith('/standard/dictionaries')
    || pathname.startsWith('/model')
    || pathname.startsWith('/metrics')
    || pathname.startsWith('/data-entry')) return 'data';
  if (pathname.startsWith('/task')) return 'task';
  if (pathname.startsWith('/dataservice')) return 'dataservice';
  if (pathname.startsWith('/mcp-management')) return 'mcp';
  if (pathname.startsWith('/service-engine') || pathname.startsWith('/compute-engine')) return 'runtime';
  if (pathname.startsWith('/system')) return 'system';
  return undefined;
};

const selectedMenuKey = (pathname: string, taskView: TaskViewConfiguration) => {
  if (pathname.startsWith('/operations/configuration')) return '/operations/configuration';
  if (pathname.startsWith('/operations/alerts')) return '/operations/alerts';
  if (pathname.startsWith('/operations')) return '/operations';
  if (pathname.startsWith('/asset-management/assets')) return '/asset-management/assets';
  if (pathname.startsWith('/asset-management/domains')) return '/asset-management/domains';
  if (pathname.startsWith('/task/masking-rules')) return '/task/masking-rules';
  if (pathname.startsWith('/task/orchestration')) return '/task/orchestration';
  if (pathname.startsWith('/task')) return taskView.id === 'all' ? undefined : taskView.path;
  if (pathname.startsWith('/system/users')) return '/system/users';
  if (pathname.startsWith('/system/roles')) return '/system/roles';
  if (pathname.startsWith('/system/permissions')) return '/system/permissions';
  if (pathname.startsWith('/system/system-mcp')) return '/system/system-mcp';
  if (pathname.startsWith('/system/configurations')) return '/system/configurations';
  if (pathname.startsWith('/system/model-warehouse-layers')) return '/system/model-warehouse-layers';
  if (pathname.startsWith('/system/ai-models')) return '/system/ai-models';
  if (pathname.startsWith('/system')) return 'system';
  if (pathname.startsWith('/datasource')) return '/datasource';
  if (pathname.startsWith('/panorama')) return '/panorama';
  if (pathname.startsWith('/file-dataset')) return '/file-dataset';
  if (pathname.startsWith('/standard/dictionaries')) return '/standard/dictionaries';
  if (pathname.startsWith('/model/field-templates')) return '/model/field-templates';
  if (pathname.startsWith('/model')) return '/model';
  if (pathname.startsWith('/metrics')) return '/metrics';
  if (pathname.startsWith('/data-entry')) return '/data-entry';
  if (pathname.startsWith('/service-engine')) return '/service-engine';
  if (pathname.startsWith('/compute-engine')) return '/compute-engine';
  if (pathname.startsWith('/dataservice/operations')) return '/dataservice/operations';
  if (pathname.startsWith('/dataservice/consumers')) return '/dataservice/consumers';
  if (pathname.startsWith('/dataservice')) return '/dataservice';
  if (pathname.startsWith('/mcp-management/invocations')) return '/mcp-management/invocations';
  if (pathname.startsWith('/mcp-management/access-tokens')) return '/mcp-management/access-tokens';
  if (pathname.startsWith('/mcp-management')) return '/mcp-management';
  return '/';
};

const breadcrumbItems = (pathname: string, taskView: TaskViewConfiguration): BreadcrumbProps['items'] => {
  if (pathname.startsWith('/operations/configuration')) return [{ title: '运行中心' }, { title: '告警配置' }];
  if (pathname.startsWith('/operations/alerts')) return [{ title: '运行中心' }, { title: '告警中心' }];
  if (pathname.startsWith('/operations')) return [{ title: '运行中心' }, { title: '运行工作台' }];
  if (pathname.startsWith('/asset-management/assets')) return [{ title: '数据资产' }, { title: '资产管理' }];
  if (pathname.startsWith('/asset-management/domains')) return [{ title: '数据资产' }, { title: '业务领域' }];
  if (pathname.startsWith('/system/users')) return [{ title: '系统管理' }, { title: '用户管理' }];
  if (pathname.startsWith('/system/roles')) return [{ title: '系统管理' }, { title: '角色管理' }];
  if (pathname.startsWith('/system/permissions')) return [{ title: '系统管理' }, { title: '权限管理' }];
  if (pathname.startsWith('/system/system-mcp')) return [{ title: '系统管理' }, { title: '系统 MCP' }];
  if (pathname.startsWith('/system/configurations')) return [{ title: '系统管理' }, { title: '系统配置' }];
  if (pathname.startsWith('/system/model-warehouse-layers')) return [{ title: '系统管理' }, { title: '数仓分层' }];
  if (pathname.startsWith('/system/ai-models')) return [{ title: '系统管理' }, { title: 'AI 模型' }];
  if (pathname.startsWith('/system')) return [{ title: '系统管理' }];
  if (/^\/datasource\/[^/]+/.test(pathname)) return [{ title: '数据管理' }, { title: <Link to="/datasource">数据源</Link> }, { title: '数据源详情' }];
  if (pathname.startsWith('/datasource')) return [{ title: '数据管理' }, { title: '数据源' }];
  if (/^\/panorama\/[^/]+/.test(pathname)) return [{ title: '数据管理' }, { title: <Link to="/panorama">全景影像</Link> }, { title: '全景详情' }];
  if (pathname.startsWith('/panorama')) return [{ title: '数据管理' }, { title: '全景影像' }];
  if (/^\/file-dataset\/[^/]+/.test(pathname)) return [{ title: '数据管理' }, { title: <Link to="/file-dataset">文件数据集</Link> }, { title: '数据集详情' }];
  if (pathname.startsWith('/file-dataset')) return [{ title: '数据管理' }, { title: '文件数据集' }];
  if (/^\/standard\/dictionaries\/[^/]+/.test(pathname)) {
    return [{ title: '数据管理' }, { title: <Link to="/standard/dictionaries">码表管理</Link> }, { title: '码表详情' }];
  }
  if (pathname.startsWith('/standard/dictionaries')) return [{ title: '数据管理' }, { title: '码表管理' }];
  if (pathname.startsWith('/model/field-templates')) return [{ title: '数据管理' }, { title: '常用字段模板' }];
  if (/^\/model\/[^/]+/.test(pathname)) return [{ title: '数据管理' }, { title: <Link to="/model">模型列表</Link> }, { title: '模型详情' }];
  if (pathname.startsWith('/model')) return [{ title: '数据管理' }, { title: '模型列表' }];
  if (pathname.startsWith('/metrics')) return [{ title: '数据管理' }, { title: '指标管理' }];
  if (/^\/data-entry\/[^/]+/.test(pathname)) return [{ title: '数据管理' }, { title: <Link to="/data-entry">数据填报</Link> }, { title: '填报详情' }];
  if (pathname.startsWith('/data-entry')) return [{ title: '数据管理' }, { title: '数据填报' }];
  if (pathname.startsWith('/task/masking-rules')) return [{ title: '任务中心' }, { title: '脱敏规则' }];
  if (pathname.startsWith('/task/orchestration')) return [{ title: '任务中心' }, { title: '任务编排' }];
  if (taskViews.some(view => view.path === pathname)) return [{ title: '任务中心' }, { title: taskView.label }];
  if (/^\/task\/[^/]+\/online-code$/.test(pathname)) {
    return [{ title: '任务中心' }, { title: <Link to={taskView.path}>{taskView.label}</Link> }, { title: '在线 Java 开发' }];
  }
  if (isTaskDefinitionEditorPath(pathname)) {
    return [{ title: '任务中心' }, { title: <Link to={taskView.path}>{taskView.label}</Link> }, { title: '任务定义' }];
  }
  if (/^\/task\/[^/]+/.test(pathname)) {
    return [{ title: '任务中心' }, { title: <Link to={taskView.path}>{taskView.label}</Link> }, { title: '任务详情' }];
  }
  if (pathname.startsWith('/task')) return [{ title: '任务中心' }, { title: taskView.label }];
  if (/^\/service-engine\/[^/]+/.test(pathname)) {
    return [{ title: '运行管理' }, { title: <Link to="/service-engine">服务引擎</Link> }, { title: '引擎详情' }];
  }
  if (pathname.startsWith('/service-engine')) return [{ title: '运行管理' }, { title: '服务引擎' }];
  if (/^\/compute-engine\/[^/]+/.test(pathname)) {
    return [{ title: '运行管理' }, { title: <Link to="/compute-engine">计算引擎</Link> }, { title: '引擎详情' }];
  }
  if (pathname.startsWith('/compute-engine')) return [{ title: '运行管理' }, { title: '计算引擎' }];
  if (pathname.startsWith('/dataservice/operations')) return [{ title: '数据服务' }, { title: '调用统计' }];
  if (pathname.startsWith('/dataservice/consumers')) return [{ title: '数据服务' }, { title: '消费者管理' }];
  if (/^\/dataservice\/[^/]+\/definition\/edit$/.test(pathname)) return [{ title: <Link to="/dataservice">数据服务</Link> }, { title: '编辑服务定义' }];
  if (/^\/dataservice\/[^/]+\/edit$/.test(pathname)) return [{ title: <Link to="/dataservice">数据服务</Link> }, { title: '编辑服务' }];
  if (/^\/dataservice\/[^/]+/.test(pathname)) return [{ title: <Link to="/dataservice">数据服务</Link> }, { title: '服务详情' }];
  if (pathname.startsWith('/dataservice')) return [{ title: '数据服务' }];
  if (pathname.startsWith('/mcp-management/invocations')) return [{ title: 'MCP 管理' }, { title: '调用日志' }];
  if (pathname.startsWith('/mcp-management/access-tokens')) return [{ title: 'MCP 管理' }, { title: '访问凭证' }];
  if (/^\/mcp-management\/[^/]+\/tools\/[^/]+\/edit$/.test(pathname)) return [{ title: <Link to="/mcp-management">MCP 管理</Link> }, { title: 'Tool 定义' }];
  if (/^\/mcp-management\/[^/]+/.test(pathname)) return [{ title: <Link to="/mcp-management">MCP 管理</Link> }, { title: 'Server 详情' }];
  if (pathname.startsWith('/mcp-management')) return [{ title: 'MCP 管理' }];
  return [{ title: '工作台' }];
};

export const AppShell = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const navigationPath = location.pathname.replace(/\/+$/, '') || '/';
  const [sidebarCollapsed, setSidebarCollapsed] = useState(readSidebarCollapsedPreference);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [openMenuOverride, setOpenMenuOverride] = useState<{ pathname: string; keys: string[] } | null>(null);
  const currentUserQuery = useCurrentUser();
  const logout = useLogout();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const navigationTask = useTask(permissions.has('task.view') ? taskIdFromPath(navigationPath) : undefined);
  const taskView = taskViews.find(view => view.path === navigationPath)
    ?? (navigationTask.isError ? getTaskView('all') : resolveTaskView(new URLSearchParams(location.search).get('taskView'), navigationTask.data?.type));
  const selectedKey = selectedMenuKey(navigationPath, taskView);
  const canViewConfigurations = permissions.has('system.configuration.view');
  const configurationsQuery = useSystemConfigurations({ page: 0, size: 20, sort: 'sortOrder,configKey' }, canViewConfigurations);
  const platformName = configurationsQuery.data?.content.find((item) => item.configKey === 'platform.name')?.configValue
    ?? 'DataScalpel';
  const platformSubtitle = configurationsQuery.data?.content.find((item) => item.configKey === 'platform.subtitle')?.configValue
    ?? '内网部署 · 模块化单体';
  const activeMenuGroup = menuGroupKey(navigationPath);
  const topLevelManagementPage = TOP_LEVEL_MANAGEMENT_PATHS.has(navigationPath);
  const dashboardPage = location.pathname === '/';
  const dataServiceEditorPage = isDataServiceEditorPath(navigationPath);
  const taskDefinitionEditorPage = isTaskDefinitionEditorPath(navigationPath);
  const mcpToolEditorPage = isMcpToolEditorPath(navigationPath);
  const editorPage = dataServiceEditorPage || taskDefinitionEditorPage || mcpToolEditorPage;
  const businessDetailPage = !topLevelManagementPage
    && !editorPage
    && navigationPath !== '/task/orchestration'
    && isBusinessDetailPath(navigationPath);
  const contentClassName = [
    'app-content',
    topLevelManagementPage ? 'app-content-management' : '',
    businessDetailPage ? 'app-content-detail' : '',
    editorPage ? 'app-content-editor' : '',
    taskDefinitionEditorPage ? 'app-content-task-definition-editor' : '',
    dashboardPage ? 'app-content-dashboard' : '',
  ].filter(Boolean).join(' ');
  const openMenuKeys = openMenuOverride?.pathname === location.pathname
    ? openMenuOverride.keys
    : activeMenuGroup ? [activeMenuGroup] : [];

  const applySidebarCollapsed = (collapsed: boolean) => {
    setSidebarCollapsed(collapsed);
    setOpenMenuOverride(null);
    writeSidebarCollapsedPreference(collapsed);
  };

  const toggleSidebar = () => applySidebarCollapsed(!sidebarCollapsed);

  const navigateFromAssistant = (pageKey: string) => {
    const path = assistantPagePath(pageKey, permissions);
    if (!path) return;
    if (path === '/assets') {
      window.open(path, '_blank', 'noopener,noreferrer');
      return;
    }
    navigate(path);
  };

  return (
    <Layout className="app-shell">
      <Layout.Sider
        breakpoint="lg"
        width={200}
        collapsedWidth={56}
        collapsed={sidebarCollapsed}
        collapsible
        trigger={null}
        className="app-sidebar"
        onBreakpoint={(broken) => {
          setSidebarCollapsed(broken ? true : readSidebarCollapsedPreference());
          setOpenMenuOverride(null);
        }}
      >
        <div className="app-brand" title={sidebarCollapsed ? platformName : undefined}>
          <span className="app-brand-mark"><img src="/data-scalpel-mark.svg" alt="" /></span>
          {!sidebarCollapsed && <span>{platformName}</span>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKey ? [selectedKey] : []}
          openKeys={openMenuKeys}
          items={navigationItems(permissions)}
          onClick={({ key }) => {
            if (key === '/assets') {
              window.open('/assets', '_blank', 'noopener,noreferrer');
              return;
            }
            navigate(key);
          }}
          onOpenChange={(nextOpenKeys) => {
            const latestOpenKey = nextOpenKeys.find((key) => !openMenuKeys.includes(String(key)));
            setOpenMenuOverride({
              pathname: location.pathname,
              keys: latestOpenKey === undefined ? [] : [String(latestOpenKey)],
            });
          }}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <div className="app-header-navigation">
            <Tooltip title={sidebarCollapsed ? '展开主菜单' : '收起主菜单'}>
              <Button
                type="text"
                className="app-sidebar-toggle"
                icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                aria-label={sidebarCollapsed ? '展开主菜单' : '收起主菜单'}
                onClick={toggleSidebar}
              />
            </Tooltip>
            <Breadcrumb items={breadcrumbItems(navigationPath, taskView)} />
          </div>
          <Space size={12}>
            <NotificationBell />
            <Tooltip title="打开 AI 助手">
              <Button
                type="text"
                className="app-assistant-trigger"
                icon={<RobotOutlined />}
                aria-label="打开 AI 助手"
                onClick={() => setAssistantOpen(true)}
              >
                AI 助手
              </Button>
            </Tooltip>
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
        <Layout.Content className={contentClassName}>
          <Outlet />
        </Layout.Content>
        <AssistantDrawer
          open={assistantOpen}
          pageKey={assistantPageKeyForPath(location.pathname)}
          sidebarCollapsed={sidebarCollapsed}
          permissions={permissions}
          onClose={() => setAssistantOpen(false)}
          onNavigatePage={navigateFromAssistant}
          onSetSidebarCollapsed={applySidebarCollapsed}
          onManageModels={() => navigate('/system/ai-models')}
        />
      </Layout>
    </Layout>
  );
};
