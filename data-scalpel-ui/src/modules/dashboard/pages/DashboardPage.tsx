import { ApartmentOutlined, ApiOutlined, DatabaseOutlined, DeploymentUnitOutlined } from '@ant-design/icons';
import { Card, Col, Row, Space, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { RuntimeOverviewCards } from '../../operations';
import { useCurrentUser } from '../../system';

const dashboardCards = [
  { title: '数据源', path: '/datasource', permission: 'datasource.view', icon: <DatabaseOutlined />, hint: '统一管理数据库、文件与 API 连接', tone: 'blue' },
  { title: '数据模型', path: '/model', permission: 'model.view', icon: <DeploymentUnitOutlined />, hint: '沉淀可复用的数据结构与标准', tone: 'purple' },
  { title: '运行任务', path: '/task', permission: 'task.view', icon: <ApartmentOutlined />, hint: '编排、调度并跟踪数据处理任务', tone: 'cyan' },
  { title: '数据服务', path: '/dataservice', permission: 'service.view', icon: <ApiOutlined />, hint: '将可信数据能力安全开放给业务', tone: 'orange' },
];

export const DashboardPage = () => {
  const user = useCurrentUser();
  return (
  <Space orientation="vertical" size={16} className="page-stack dashboard-page">
    <div className="dashboard-welcome">
      <span className="dashboard-welcome-kicker">DATA WORKSPACE</span>
      <Typography.Title level={2}>欢迎使用 DataScalpel</Typography.Title>
      <Typography.Paragraph type="secondary">集中管理数据连接、模型、任务与服务，让数据能力从接入到交付保持清晰可控。</Typography.Paragraph>
    </div>
    <RuntimeOverviewCards />
    <Row gutter={[14, 14]} className="dashboard-resource-grid">
      {dashboardCards.filter(card => user.data?.permissions.includes(card.permission)).map((card) => (
        <Col key={card.title} xs={24} sm={12} xl={6}>
          <Card className="dashboard-resource-card">
            <div className={`dashboard-resource-icon dashboard-resource-icon-${card.tone}`}>{card.icon}</div>
            <Link to={card.path}><Typography.Title level={4}>{card.title}</Typography.Title></Link>
            <Typography.Text type="secondary" className="dashboard-resource-hint">{card.hint}</Typography.Text>
          </Card>
        </Col>
      ))}
    </Row>
  </Space>
  );
};
