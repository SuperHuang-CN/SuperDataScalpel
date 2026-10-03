import type { UseQueryResult } from '@tanstack/react-query';
import { Button, Empty, Segmented, Tooltip } from 'antd';
import { Link } from 'react-router-dom';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import type { RuntimeOverview as RuntimeData } from '../../operations';
import { runtimeBuckets, runtimeHref } from '../model/dashboardCharts';
import { DashboardPanel } from './DashboardPanel';
import { number, dateTime } from '../model/dashboardFormat';
export const RuntimeOverview = ({ query, days, onDaysChange }: {
 query: UseQueryResult<RuntimeData>; days: number; onDaysChange: (days: number) => void;
}) => {
 const data = query.data; const tasks = data?.tasks;
 const buckets = data ? runtimeBuckets(data) : [];
 const max = Math.max(1, ...buckets.map(bucket => bucket.success + bucket.failed + bucket.other));
 const samples = buckets.reduce((total, bucket) => total + bucket.success + bucket.failed + bucket.other, 0);
 const completed = Object.values(tasks?.completed ?? {}).reduce((sum, count) => sum + (count ?? 0), 0);
 const results = [
  { label: '执行成功', count: tasks?.completed.SUCCESS ?? 0, status: 'SUCCESS', tone: 'success' },
  { label: '失败 / 超时', count: (tasks?.completed.FAILED ?? 0) + (tasks?.completed.TIMED_OUT ?? 0), status: 'FAILED_OR_TIMED_OUT', tone: 'failed' },
  { label: '取消', count: tasks?.completed.CANCELLED ?? 0, status: 'CANCELLED', tone: 'other' },
  { label: '停止', count: tasks?.completed.STOPPED ?? 0, status: 'STOPPED', tone: 'other' },
  { label: '跳过', count: tasks?.completed.SKIPPED ?? 0, status: 'SKIPPED', tone: 'other' },
 ];
 return <DashboardPanel className="home-runtime-panel" title="任务完成情况" query={query} action={<Segmented size="small" value={days} options={[{ label: '近24小时', value: 1 }, { label: '近7天', value: 7 }]} onChange={onDaysChange} />}>
 {data && tasks && <>
  <div className="home-metrics">
   <Link to={runtimeHref(data.from, data.to, 'SUCCESS')}><span>执行成功</span><strong>{number(tasks.completed.SUCCESS ?? 0)}</strong></Link>
   <Link to={runtimeHref(data.from, data.to, 'FAILED_OR_TIMED_OUT')}><span>失败 / 超时</span><strong className="home-danger">{number((tasks.completed.FAILED ?? 0) + (tasks.completed.TIMED_OUT ?? 0))}</strong></Link>
   <div><span>执行成功率 <ContextHelp ariaLabel="执行成功率计算方式" content="成功次数 ÷（成功 + 失败 + 超时次数）。取消、停止、跳过不参与计算；没有这些完成记录时显示破折号。仅统计正式非实时运行，执行成功不代表质检结论通过。" /></span><strong className={tasks.successRate === null ? 'home-neutral' : 'home-success'}>{tasks.successRate === null ? '—' : (tasks.successRate * 100).toFixed(1) + '%'}</strong></div>
   <Link to="/operations?tab=runs&active=true&status=QUEUED"><span>当前排队</span><strong>{number(tasks.current.QUEUED ?? 0)}</strong></Link>
  </div>
  {samples > 0 && samples === completed ? <><div className="home-trend-heading"><span>已完成任务 · {days === 1 ? '按小时' : '按日'}统计</span><span>单位：次</span></div><div className="home-runtime-chart" aria-label="非实时运行完成趋势">
    <div className="home-chart-axis"><span>{number(max)}</span><span>{number(Math.round(max / 2))}</span><span>0</span></div>
    <div className="home-chart-columns">{buckets.map((bucket, index) => <Tooltip key={bucket.from} title={`${dateTime(bucket.from)} — ${dateTime(bucket.to)}：成功 ${bucket.success}，失败 ${bucket.failed}，其他 ${bucket.other}`}>
      <Link className="home-chart-column" to={runtimeHref(bucket.from, bucket.to)} aria-label={`查看${dateTime(bucket.from)}结束的运行`}>
       <div className="home-column-stack">
        <span className="home-bar-other" style={{ height: bucket.other / max * 100 + '%' }} />
        <span className="home-bar-failed" style={{ height: bucket.failed / max * 100 + '%' }} />
        <span className="home-bar-success" style={{ height: bucket.success / max * 100 + '%' }} />
       </div>
       <small>{index % Math.max(1, Math.ceil(buckets.length / 7)) === 0 ? days === 1 ? new Date(bucket.from).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : new Date(bucket.from).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : ''}</small>
      </Link>
    </Tooltip>)}</div>
   </div></> : completed > 0 ? <div className="home-runtime-distribution">
     <div className="home-trend-heading"><span>已完成任务 · 结果分布</span><span>共 {number(completed)} 次</span></div>
     {results.filter(item => item.count > 0).map(item => <Link key={item.status} to={runtimeHref(data.from, data.to, item.status)} className="home-result-row">
       <span>{item.label}</span><span className="home-result-track"><i className={`home-bar-${item.tone}`} style={{ width: `${item.count / completed * 100}%` }} /></span><b>{number(item.count)} 次</b>
     </Link>)}
     <div className="home-trend-heading"><span>分时数据与汇总不一致，暂按完成结果展示</span><Button type="link" size="small" onClick={() => void query.refetch()}>重新加载</Button></div>
   </div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><strong>{days === 1 ? '近24小时' : '近7天'}没有已完成的任务</strong><span>统计批处理、质检和工作流；排队中、运行中的任务不计入完成次数。</span></>} />}
  <div className="home-chart-legend"><span><i className="home-bar-success" />成功</span><span><i className="home-bar-failed" />失败 / 超时</span><span><i className="home-bar-other" />取消 / 停止 / 跳过</span><ContextHelp ariaLabel="任务执行统计口径" content="统计正式非实时运行实例，含质检与工作流；取消、停止与跳过不计入成功率。当前排队为实时状态，不受所选历史窗口限制。" /><Link to={runtimeHref(data.from, data.to)}>查看运行记录 ↗</Link></div>
 </>}
 </DashboardPanel>;
};

