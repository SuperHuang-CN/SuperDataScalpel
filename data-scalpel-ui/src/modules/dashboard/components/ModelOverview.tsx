import { DatabaseOutlined } from '@ant-design/icons';
import type { UseQueryResult } from '@tanstack/react-query';
import { Button } from 'antd';
import { Link } from 'react-router-dom';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import type { ModelStatistics } from '../../model';
import type { AssetStatistics } from '../../asset';
import { DashboardPanel } from './DashboardPanel';
import { number } from '../model/dashboardFormat';
import { CategoryBarChart } from './CategoryBarChart';
export const ModelOverview = ({ query, assets, canViewAssets }: { query: UseQueryResult<ModelStatistics>; assets: UseQueryResult<AssetStatistics>; canViewAssets: boolean }) => {
 const data = query.data;
 return <DashboardPanel title="模型建设" icon={<DatabaseOutlined />} action={<Link to="/model">查看模型 ↗</Link>} query={query}>
  {data && <>
   <div className="home-primary"><Link to="/model?status=PUBLISHED">{number(data.published)}</Link><span>已发布模型</span>
    <Link className="home-secondary-link" to="/model?status=DRAFT">草稿 {number(data.draft)}</Link>
    <ContextHelp ariaLabel="模型建设统计口径" content={`发布代表结构契约，不表示数据最新或可用。已发布中平台建表 ${data.managed} 个、绑定已有表 ${data.external} 个。`} />
   </div>
   <CategoryBarChart label="已发布模型 · 分层分布" data={data.layers.map(layer => ({
     key: layer.id ?? 'unassigned', label: layer.code ? `${layer.code} · ${layer.name}` : layer.name,
     count: layer.count, href: '/model?' + new URLSearchParams({ status: 'PUBLISHED', layer: layer.id ?? 'unassigned' }),
   }))} />
  </>}
  {canViewAssets && <div className="home-foot">
   {assets.error ? <InlineFeedback tone="error" label="资产摘要加载失败" action={<Button type="link" onClick={() => void assets.refetch()}>重试</Button>} />
    : assets.data ? <>
      <Link to="/asset-management/assets?status=PUBLISHED">已发布资产 <b>{number(assets.data.published)}</b></Link>
      <Link to="/asset-management/assets?status=PUBLISHED&syncStatus=OUTDATED">待同步 <b>{number(assets.data.outdated)}</b></Link>
      {assets.data.sourceIssues > 0 && <span className="home-warning">来源异常 {number(assets.data.sourceIssues)}</span>}
     </> : <span>资产摘要加载中…</span>}
  </div>}
 </DashboardPanel>;
};

