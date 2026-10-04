import type { UseQueryResult } from '@tanstack/react-query';
import { Button, Empty, Tooltip } from 'antd';
import { Link } from 'react-router-dom';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import type { GatewayUsage, GatewayAccessTrend, GatewayAccessOverview } from '../../dataservice';
import { usageBuckets } from '../model/dashboardCharts';
import { DashboardPanel } from './DashboardPanel';
import { number, dateTime } from '../model/dashboardFormat';

export const ServiceUsage = ({ query, trend, overview }: {
  query: UseQueryResult<GatewayUsage>; trend: UseQueryResult<GatewayAccessTrend>; overview: UseQueryResult<GatewayAccessOverview>;
}) => {
  const data = query.data;
  const metrics = overview.data;
  const buckets = trend.data ? usageBuckets(trend.data) : [];
  const max = Math.max(1, ...buckets.map(bucket => bucket.requests));
  const x = (index: number) => 30 + index * 600 / Math.max(1, buckets.length - 1);
  const y = (count: number) => 124 - count / max * 105;
  const path = buckets.map((bucket, index) => `${index ? 'L' : 'M'} ${x(index)} ${y(bucket.requests)}`).join(' ');
  const hasRequests = Boolean(metrics && metrics.requestCount > 0);
  return <DashboardPanel className="home-usage-panel" title="网关调用量与响应表现" query={overview} action={<span className="home-caption">近7天 · 完整小时</span>}>
    {metrics && <div className="home-metrics home-service-metrics">
      <div><span>调用总量 <small>次</small></span><strong>{number(metrics.requestCount)}</strong></div>
      <div><span>HTTP 成功率 <ContextHelp ariaLabel="HTTP成功率计算方式" content="2xx响应次数 ÷ 请求总数；HTTP成功不代表业务结果正确。无请求时不计算。" /></span><strong className="home-success">{hasRequests ? `${(metrics.successRate * 100).toFixed(1)}%` : '—'}</strong></div>
      <div><span>平均响应耗时 <small>ms</small></span><strong>{metrics.averageRequestLatencyMs === null ? '—' : number(Math.round(metrics.averageRequestLatencyMs))}</strong></div>
      <div><span>服务端错误 <small>5xx</small></span><strong className={metrics.status5xxCount ? 'home-danger' : ''}>{number(metrics.status5xxCount)}</strong></div>
    </div>}
    {data && !data.collectionEnabled && <InlineFeedback tone="warning" label="网关日志接收未开启" detail="历史记录仍可查看；接收开关不证明链路健康。" />}
    {trend.error && <InlineFeedback tone="error" label="调用趋势加载失败" action={<Button type="link" onClick={() => void trend.refetch()}>重试</Button>} />}
    {trend.isPending ? <div className="home-chart-pending">调用趋势加载中…</div> : trend.data && (
      buckets.some(bucket => bucket.requests > 0) ? <div className="home-usage-chart home-interactive-chart">
        <div className="home-trend-heading"><span>请求次数趋势</span><span><i className="home-bar-success" />请求总数 <i className="home-bar-failed" />5xx错误</span></div>
        <div className="home-usage-plot">
          <div className="home-usage-scale" aria-hidden><span>{number(max)}</span><span>{number(Math.round(max / 2))}</span><span>0</span></div>
          <svg viewBox="0 0 660 155" preserveAspectRatio="none" aria-hidden>
            {[20, 72, 124].map(lineY => <line key={lineY} x1="30" x2="630" y1={lineY} y2={lineY} stroke="#e3ecf1" />)}
            <path d={path} fill="none" stroke="#347f9f" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
            {buckets.map((bucket, index) => <rect key={bucket.at} x={x(index) - 4} y={y(bucket.errors)} width="8" height={bucket.errors / max * 105} fill="#d7808a" />)}
          </svg>
          {buckets.map((bucket, index) => {
            const left = index ? (x(index - 1) + x(index)) / 2 : 0;
            const right = index === buckets.length - 1 ? 660 : (x(index) + x(index + 1)) / 2;
            const end = new Date(Math.min(Date.parse(bucket.at) + 86400_000, Date.parse(trend.data!.toExclusive))).toISOString();
            const description = `${dateTime(bucket.at)} 至 ${dateTime(end)}，请求 ${number(bucket.requests)} 次，5xx错误 ${number(bucket.errors)} 次`;
            return <Tooltip key={bucket.at} trigger={['hover', 'focus', 'click']} title={<div className="home-trend-tooltip"><span>{dateTime(bucket.at)} — {dateTime(end)}</span><b>请求总数：{number(bucket.requests)} 次</b><b>5xx错误：{number(bucket.errors)} 次</b></div>}>
              <button type="button" className="home-chart-hit" aria-label={description} style={{ left: `${left / 660 * 100}%`, width: `${(right - left) / 660 * 100}%` }}>
                <span className="home-chart-guide" style={{ left: `${(x(index) - left) / (right - left) * 100}%` }} />
                <span className="home-chart-point" style={{ left: `${(x(index) - left) / (right - left) * 100}%`, top: `${y(bucket.requests) / 155 * 100}%` }} />
              </button>
            </Tooltip>;
          })}
        </div>
        <div className="home-usage-labels">{buckets.map(bucket => <span key={bucket.at}>{new Date(bucket.at).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })}</span>)}</div>
      </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="近7天暂无已记录的网关调用" />
    )}
    <div className="home-foot home-usage-support">
      {query.error ? <InlineFeedback tone="error" label="调用主体摘要加载失败" action={<Button type="link" onClick={() => void query.refetch()}>重试</Button>} /> : data ? <>
        <span>已识别服务：成功调用 <b>{number(data.successCount)}</b> 次</span>
        <span>调用服务 <b>{number(data.activeServices)}</b></span><span>调用方 <b>{number(data.activeConsumers)}</b></span>
        <ContextHelp ariaLabel="调用主体统计范围" content={`仅统计已识别服务且身份无冲突的网关小时日志，不包含直连Engine或未识别服务的请求。服务数与调用方数仅统计有2xx响应的已识别主体；5xx错误 ${number(data.serverErrorCount)} 次。统计截至 ${dateTime(data.to)}；最近记录 ${dateTime(data.latestRecordedHour)}，不证明采集完整。`} />
      </> : <span>调用主体摘要加载中…</span>}
      <Link to="/dataservice/operations?range=7d">查看调用分析 ↗</Link>
    </div>
  </DashboardPanel>;
};
