import { ApartmentOutlined, ApiOutlined, DatabaseOutlined, DeploymentUnitOutlined } from '@ant-design/icons';
import { Card, Col, Row, Space, Statistic, Typography } from 'antd';

const dashboardCards = [
  { title: '数据源', value: 0, icon: <DatabaseOutlined />, hint: '统一管理数据库、文件与 API 连接', tone: 'blue' },
  { title: '数据模型', value: 0, icon: <DeploymentUnitOutlined />, hint: '沉淀可复用的数据结构与标准', tone: 'purple' },
  { title: '运行任务', value: 0, icon: <ApartmentOutlined />, hint: '编排、调度并跟踪数据处理任务', tone: 'cyan' },
  { title: '已发布服务', value: 0, icon: <ApiOutlined />, hint: '将可信数据能力安全开放给业务', tone: 'orange' },
];

export const DashboardPage = () => (
  <Space orientation="vertical" size={16} className="page-stack dashboard-page">
    <div className="dashboard-welcome">
      <span className="dashboard-welcome-kicker">DATA WORKSPACE</span>
      <Typography.Title level={2}>欢迎使用 DataScalpel</Typography.Title>
      <Typography.Paragraph type="secondary">集中管理数据连接、模型、任务与服务，让数据能力从接入到交付保持清晰可控。</Typography.Paragraph>
    </div>
    <Row gutter={[14, 14]} className="dashboard-resource-grid">
      {dashboardCards.map((card) => (
        <Col key={card.title} xs={24} sm={12} xl={6}>
          <Card className="dashboard-resource-card">
            <div className={`dashboard-resource-icon dashboard-resource-icon-${card.tone}`}>{card.icon}</div>
            <Statistic title={card.title} value={card.value} />
            <Typography.Text type="secondary" className="dashboard-resource-hint">{card.hint}</Typography.Text>
          </Card>
        </Col>
      ))}
    </Row>
  </Space>
);
