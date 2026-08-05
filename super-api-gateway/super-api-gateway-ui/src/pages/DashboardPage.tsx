import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Row, Statistic, Table, Tag } from 'antd';
import { api } from '../api';
import type { RuntimeSummary } from '../model';

export const DashboardPage = () => {
  const query = useQuery({ queryKey: ['runtime'], queryFn: () => api<RuntimeSummary>('/runtime'), refetchInterval: 10_000 });
  if (query.error) return <Alert type="error" showIcon message="运行状态加载失败" description={(query.error as Error).message} />;
  const data = query.data;
  const statistics = [
    ['服务', data?.services ?? 0],
    ['路由', data?.routes ?? 0],
    ['Consumer', data?.consumers ?? 0],
    ['API Key', data?.apiKeys ?? 0],
    ['订阅', data?.subscriptions ?? 0],
    ['配置 Revision', data?.targetRevision ?? 0],
  ] as const;
  return (
    <div className="page-stack">
      <Row gutter={[12, 12]}>
        {statistics.map(([title, value]) => (
          <Col span={4} key={title}><Card><Statistic title={title} value={value} /></Card></Col>
        ))}
      </Row>
      <Card title="数据面实例" className="fill-card">
        <Table
          rowKey="id"
          loading={query.isLoading}
          dataSource={data?.instances ?? []}
          pagination={false}
          columns={[
            { title: '实例', dataIndex: 'id', ellipsis: true },
            { title: '状态', dataIndex: 'state', width: 110, render: (state: string) => <Tag color={state === 'READY' ? 'success' : 'error'}>{state}</Tag> },
            { title: '已加载 Revision', dataIndex: 'loadedRevision', width: 150 },
            { title: '版本', dataIndex: 'applicationVersion', width: 130 },
            { title: '最近心跳', dataIndex: 'lastSeenAt', width: 190 },
            { title: '最近错误', dataIndex: 'lastError', ellipsis: true, render: (value?: string) => value || '—' },
          ]}
        />
      </Card>
    </div>
  );
};
