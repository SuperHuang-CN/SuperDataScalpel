import {
  ApiOutlined,
  DashboardOutlined,
  DeploymentUnitOutlined,
  LogoutOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu, Space, Typography } from 'antd';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { authToken } from './api';
import { ConsumersPage } from './pages/ConsumersPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { RuntimePage } from './pages/RuntimePage';
import { ServicesPage } from './pages/ServicesPage';

const ProtectedApp = () => {
  const navigate = useNavigate();
  const location = useLocation();
  if (!authToken.get()) return <Navigate to="/login" replace />;

  const selected = location.pathname.startsWith('/services')
    ? '/services'
    : location.pathname.startsWith('/consumers')
      ? '/consumers'
      : location.pathname.startsWith('/runtime')
        ? '/runtime'
        : '/';

  return (
    <Layout className="app-shell">
      <Layout.Sider width={220} theme="dark">
        <div className="brand"><ApiOutlined /> Super API Gateway</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          items={[
            { key: '/', icon: <DashboardOutlined />, label: '仪表盘' },
            { key: '/services', icon: <DeploymentUnitOutlined />, label: '服务与路由' },
            { key: '/consumers', icon: <TeamOutlined />, label: 'Consumer' },
            { key: '/runtime', icon: <ApiOutlined />, label: '运行节点' },
          ]}
          onClick={({ key }) => navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Typography.Text strong>网关管理</Typography.Text>
          <Space>
            <Typography.Text type="secondary">admin</Typography.Text>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={() => {
                authToken.clear();
                navigate('/login', { replace: true });
              }}
            >
              退出
            </Button>
          </Space>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Routes>
            <Route index element={<DashboardPage />} />
            <Route path="services" element={<ServicesPage />} />
            <Route path="consumers" element={<ConsumersPage />} />
            <Route path="runtime" element={<RuntimePage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Layout.Content>
      </Layout>
    </Layout>
  );
};

export const App = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/*" element={<ProtectedApp />} />
  </Routes>
);
