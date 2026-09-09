import { Card, Statistic, Button, Space } from 'antd';
import { Link } from 'react-router-dom';
import { InlineFeedback, ContextHelp } from '../../../shared/components/ContextualFeedback';
import { useRuntimeOverview } from '../hooks/useOperations';
import './operations.css';

export const RuntimeOverviewCards = ({ range }: { range?: { from?: string; to?: string } }) => {
  const query = useRuntimeOverview(range);
  const data = query.data;
  const cards = [
    ...(data?.tasks ? [
      { key: 'queued', title: '当前排队', value: data.tasks.current.QUEUED ?? 0, url: '/operations?tab=runs&active=true&status=QUEUED' },
      { key: 'running', title: '当前运行', value: data.tasks.current.RUNNING ?? 0, url: '/operations?tab=runs&active=true&status=RUNNING' },
      { key: 'failed', title: '窗口内批任务失败', value: (data.tasks.completed.FAILED ?? 0) + (data.tasks.completed.TIMED_OUT ?? 0), url: `/operations?tab=runs&timeField=endedAt&batch=true&status=FAILED_OR_TIMED_OUT&from=${encodeURIComponent(data.from)}&to=${encodeURIComponent(data.to)}` },
      { key: 'quality', title: '窗口内质检不通过', value: data.tasks.qualityFailed, url: `/operations?tab=runs&timeField=endedAt&batch=true&taskType=SPARK_MODEL_QUALITY&quality=FAILED&from=${encodeURIComponent(data.from)}&to=${encodeURIComponent(data.to)}` },
    ] : []),
    { key: 'alerts', title: '未关闭告警', value: data?.openAlerts, url: '/operations/alerts' },
    ...(data?.engines ? [
      { key: 'engines', title: '需关注引擎', value: data.engines.unreachable + data.engines.notReady, url: '/operations?tab=engines' },
      { key: 'unknown', title: '引擎观测不可用', value: data.engines.unknown, url: '/operations?tab=engines' },
    ] : []),
  ];
  return <div className="ops-overview-cards-container">
    {query.error && <InlineFeedback tone="error" label="运行摘要加载失败" detail={query.error.message} action={<Button type="link" onClick={() => void query.refetch()}>重试</Button>} />}
    <div className="ops-overview-cards">{cards.map(card => <Link key={card.key} to={card.url}>
      <Card loading={query.isPending}><Statistic title={card.title} value={card.value ?? '—'} /></Card>
    </Link>)}</div>
    {data && <Space wrap className="ops-overview-caption">
      {data.tasks && <span>技术成功率：{data.tasks.successRate === null ? '无样本' : `${(data.tasks.successRate * 100).toFixed(1)}%`}</span>}
      <ContextHelp ariaLabel="统计口径" content="默认仅统计正式运行。当前排队和运行覆盖全部活动实例；历史指标按结束时间统计。取消、跳过和实时部署不进入批任务成功率，质量结论单独计算。" />
      {data.pendingSignals !== null && data.pendingSignals > 0 && <InlineFeedback tone="warning" label={`待处理告警信号 ${data.pendingSignals} 条`} />}
      {data.failedDeliveries !== null && data.failedDeliveries > 0 && <Link to="/operations/configuration?tab=deliveries">通知投递失败 {data.failedDeliveries} 条</Link>}
    </Space>}
  </div>;
};
