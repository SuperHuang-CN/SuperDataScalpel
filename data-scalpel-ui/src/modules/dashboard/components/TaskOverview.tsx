import { ApartmentOutlined } from '@ant-design/icons';
import type { UseQueryResult } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { taskViews, type TaskStatistics } from '../../task';
import type { RuntimeOverview as RuntimeData } from '../../operations';
import { DashboardPanel } from './DashboardPanel';
import { number } from '../model/dashboardFormat';
import { TaskDistributionChart } from './OverviewDistributionCharts';
export const TaskOverview = ({ query, runtime }: { query: UseQueryResult<TaskStatistics>; runtime: UseQueryResult<RuntimeData> }) =>
 <DashboardPanel title="任务生产" icon={<ApartmentOutlined />} action={<Link to="/task">进入任务中心 ↗</Link>} query={query}>
 {query.data && <>
  <div className="home-primary"><Link to="/task">{number(query.data.total)}</Link><span>任务定义</span><ContextHelp ariaLabel="任务数量统计口径" content="任务定义、运行实例与实时部署分别计数。质检结束次数随任务执行视图选择的时间窗口统计。" /></div>
  <TaskDistributionChart data={taskViews.filter(view => view.id !== 'all').map(view => {
   const counts = query.data?.types.filter(row => view.types.includes(row.type)) ?? [];
   const metrics = runtime.data?.tasks?.types?.filter(row => view.types.includes(row.type));
   const running = metrics?.reduce((n, row) => n + (row.current.RUNNING ?? 0), 0) ?? 0;
   const queued = metrics?.reduce((n, row) => n + (row.current.QUEUED ?? 0), 0) ?? 0;
   const ending = metrics?.reduce((n, row) => n + (row.current.CANCEL_REQUESTED ?? 0) + (row.current.STOP_REQUESTED ?? 0), 0) ?? 0;
   const finished = metrics?.reduce((n, row) => n + Object.values(row.completed).reduce((sum, count) => sum + (count ?? 0), 0), 0) ?? 0;
   const deployments = metrics?.reduce((n, row) => n + (row.deployments.RUNNING ?? 0), 0) ?? 0;
   const stopping = metrics?.reduce((n, row) => n + (row.deployments.STOPPING ?? 0) + (row.deployments.STARTING ?? 0), 0) ?? 0;
   return { key: view.id, label: view.label, href: view.path, count: counts.reduce((n, row) => n + row.count, 0),
    detail: runtime.error ? '运行摘要刷新失败' : !metrics ? '运行摘要加载中' :
      view.id === 'streaming' ? `运行部署 ${deployments} · 启停中 ${stopping}` :
      view.id === 'quality' ? `窗口内结束 ${finished} 次` :
      `运行 ${running} · 排队 ${queued}${ending ? ` · 取消/停止中 ${ending}` : ''}` };
  })} />
 </>}
 </DashboardPanel>;

