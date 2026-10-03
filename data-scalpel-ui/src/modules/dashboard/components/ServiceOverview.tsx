import { ApiOutlined } from '@ant-design/icons';
import type { UseQueryResult } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { dataServiceTypeLabels, type ServiceStatistics } from '../../dataservice';
import { DashboardPanel } from './DashboardPanel';
import { number } from '../model/dashboardFormat';
import { ServiceDistributionChart } from './OverviewDistributionCharts';
export const ServiceOverview = ({ query }: { query: UseQueryResult<ServiceStatistics> }) =>
 <DashboardPanel title="服务交付" icon={<ApiOutlined />} action={<Link to="/dataservice">查看服务 ↗</Link>} query={query}>
 {query.data && <>
 <div className="home-primary"><Link to="/dataservice?status=ENABLED">{number(query.data.enabled)}</Link><span>已启用服务</span>
  <span className="home-secondary-link home-warning">部署异常 {number(query.data.failed)}</span>
 </div>
 <ServiceDistributionChart data={query.data.types.map(row => ({
   key: row.type, label: dataServiceTypeLabels[row.type], count: row.enabled,
   href: '/dataservice?' + new URLSearchParams({ status: 'ENABLED', type: row.type }),
   detail: `部署异常 ${number(row.failed)}`, errorCount: row.failed,
 }))} />
 <div className="home-foot"><span>已发布网关 <b>{number(query.data.gatewayPublished)}</b></span>
  <span>部署未确认 {number(query.data.unconfirmed)}</span>
  <ContextHelp ariaLabel="服务交付统计口径" content="已启用不等同实时可用。部署异常仅指FAILED；未确认包括处理中、已移除和缺失记录。网关发布按服务去重，是独立发布状态。" />
 </div>
 </>}
 </DashboardPanel>;

