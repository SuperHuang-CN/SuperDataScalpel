import { ApartmentOutlined, ApiOutlined, DatabaseOutlined, DeploymentUnitOutlined } from '@ant-design/icons';
import { Card, Col, Row, Space, Statistic, Typography } from 'antd';

const dashboardCards = [
  { title: '数据源', value: 0, icon: <DatabaseOutlined />, hint: '等待接入数据源管理能力' },
  { title: '数据模型', value: 0, icon: <DeploymentUnitOutlined />, hint: '等待接入模型管理能力' },
  { title: '运行任务', value: 0, icon: <ApartmentOutlined />, hint: '等待接入任务管理能力' },
  { title: '已发布服务', value: 0, icon: <ApiOutlined />, hint: '等待接入服务管理能力' },
];

export const DashboardPage = () => (
  <Space orientation="vertical" size={24} className="page-stack">
    <div>
      <Typography.Title level={2}>工作台</Typography.Title>
      <Typography.Paragraph type="secondary">DataScalpel 前端骨架已就绪。业务能力将按系统、数据源、模型、任务和数据服务逐步建设。</Typography.Paragraph>
    </div>
    <Row gutter={[16, 16]}>
      {dashboardCards.map((card) => (
        <Col key={card.title} xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title={card.title} value={card.value} prefix={card.icon} />
            <Typography.Text type="secondary">{card.hint}</Typography.Text>
          </Card>
        </Col>
      ))}
    </Row>
  </Space>
);
