import type { UseQueryResult } from '@tanstack/react-query';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { Empty, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { severityLabels, statusLabels, type AlertIncident } from '../../operations';
import { DashboardPanel } from './DashboardPanel';
import { number, dateTime } from '../model/dashboardFormat';
export const AttentionList = ({ query }: { query: UseQueryResult<PageResponse<AlertIncident>> }) =>
 <DashboardPanel className="home-alert-panel" title="待处理告警" action={<Link to="/operations/alerts">全部 ↗</Link>} query={query}>
 {query.data && <>
 <div className="home-alert-count">未关闭告警 <b>{number(query.data.totalElements)}</b></div>
 <div className="home-alerts">{query.data.content.map(alert => <Link className="home-alert-row" key={alert.id} to={'/operations/alerts?incident=' + alert.id}>
  <Tag color={alert.severity === 'CRITICAL' ? 'error' : 'warning'}>{severityLabels[alert.severity]}</Tag>
  <div><strong>{alert.subjectName}</strong><p>{alert.summary}</p><small>{statusLabels[alert.status]} · {dateTime(alert.occurredAt)}</small></div>
  <span className="home-alert-action">查看 →</span>
 </Link>)}</div>
 {query.data.content.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有未关闭告警" />}
 </>}
 </DashboardPanel>;

