import { useState } from 'react';
import { ReloadOutlined, DatabaseOutlined, FolderOpenOutlined, EnvironmentOutlined, BarChartOutlined, FormOutlined } from '@ant-design/icons';
import { Button, Empty, Skeleton, Tabs } from 'antd';
import { Link } from 'react-router-dom';
import { useCurrentUser } from '../../system';
import { useDashboardQueries } from '../hooks/useDashboardQueries';
import { useDashboardResourceCounts } from '../hooks/useDashboardResourceCounts';
import { number } from '../model/dashboardFormat';
import { ModelOverview } from '../components/ModelOverview';
import { TaskOverview } from '../components/TaskOverview';
import { ServiceOverview } from '../components/ServiceOverview';
import { RuntimeOverview } from '../components/RuntimeOverview';
import { AttentionList } from '../components/AttentionList';
import { QualityOverview } from '../components/QualityOverview';
import { ServiceUsage } from '../components/ServiceUsage';
import './dashboard.css';

const shortcuts = [
  { key: 'sources', label: '数据源', path: '/datasource', permission: 'datasource.view', icon: <DatabaseOutlined /> },
  { key: 'files', label: '文件数据集', path: '/file-dataset', permission: 'filedataset.view', icon: <FolderOpenOutlined /> },
  { key: 'forms', label: '数据填报', path: '/data-entry', permission: 'dataentry.view', icon: <FormOutlined /> },
  { key: 'metrics', label: '业务指标', path: '/metrics', permission: 'metric.view', icon: <BarChartOutlined /> },
  { key: 'panoramas', label: '全景影像', path: '/panorama', permission: 'panorama.view', icon: <EnvironmentOutlined /> },
] as const;
export const DashboardPage = () => {
  const user = useCurrentUser();
  const [days, setDays] = useState(1);
  const permissions = user.data?.permissions ?? [];
  const can = (permission: string) => permissions.includes(permission);
  const queries = useDashboardQueries(permissions, days);
  const resources = useDashboardResourceCounts(permissions);
  const links = shortcuts.filter(item => can(item.permission));
  const hasOverview = can('model.view') || can('task.view') || can('service.view');
  return <div className="homepage-workbench">
    <header className="home-welcome">
      <div><h1>数据工作台</h1><p>从模型建设到服务交付，关注生产进展与需要处理的问题。</p></div>
      <Button type="primary" size="middle" icon={<ReloadOutlined />} loading={queries.refreshing || resources.refreshing} onClick={() => { void queries.refresh(); void resources.refresh(); }}>刷新数据</Button>
    </header>
    {user.isPending ? <Skeleton active /> : <>
      <div className="home-studio-layout">
      {hasOverview && <div className="home-workspace-sheet">
        <div className="home-overview">
          <div className="home-top-grid" aria-label="建设与交付概览">
            {can('model.view') && <ModelOverview query={queries.models} assets={queries.assets} canViewAssets={can('asset.view')} />}
            {can('task.view') && <TaskOverview query={queries.tasks} runtime={queries.runtime} />}
            {can('service.view') && <ServiceOverview query={queries.services} />}
          </div>
          {(can('service.view') || can('task.view')) && <Tabs className="home-analysis-tabs" size="small" items={[
            ...(can('service.view') ? [{ key: 'usage', label: '服务调用', children: <ServiceUsage query={queries.usage} trend={queries.trend} overview={queries.accessOverview} /> }] : []),
            ...(can('task.view') ? [{ key: 'runtime', label: '任务执行', children: <RuntimeOverview query={queries.runtime} days={days} onDaysChange={setDays} /> }] : []),
          ]} />}
        </div>
      </div>}
      {(queries.canObserve || can('model.view')) && <aside className="home-attention-rail" aria-label="告警与数据质量">
        {queries.canObserve && <AttentionList query={queries.alerts} />}
        {can('model.view') && <QualityOverview query={queries.quality} />}
      </aside>}
      </div>
      {links.length > 0 && <nav className="home-shortcuts" aria-label="资源快捷入口">
        <span className="home-shortcuts-title">资源快捷入口</span>
        <div className="home-shortcuts-links">{links.map(item => {
          const query = resources.counts[item.key];
          const count = query.isError ? '—' : query.data ? number(query.data.totalElements) : '…';
          const description = query.isError ? '数量加载失败，仍可进入列表' : query.data ? `已登记 ${count} 项` : '数量加载中';
          return <Link key={item.key} to={item.path} title={`${item.label}：${description}`}>
            {item.icon}<span>{item.label}</span><strong aria-label={description}>{count}</strong>
          </Link>;
        })}</div>
      </nav>}
      {!can('model.view') && !can('task.view') && !can('service.view') && !queries.canObserve && !links.length
        && <Empty description="当前账号暂无可展示的业务统计，可从侧栏进入已授权功能" />}
    </>}
  </div>;
};
