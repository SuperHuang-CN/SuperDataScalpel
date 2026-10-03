import { dateTime } from '../model/dashboardFormat';
import { Button, Skeleton } from 'antd';
import type { ReactNode } from 'react';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
export interface PanelQuery {
 isPending: boolean; error: Error | null; dataUpdatedAt: number; refetch: () => Promise<unknown>;
}
export const DashboardPanel = ({ title, action, query, children, className = '', icon }: {
 title: string; action?: ReactNode; query: PanelQuery; children: ReactNode; className?: string; icon?: ReactNode;
}) => <section className={'home-panel ' + className} aria-label={title}>
 <header className="home-panel-heading"><h2>{icon && <span className="home-heading-icon" aria-hidden>{icon}</span>}{title}</h2><div className="home-panel-actions">{action}{query.dataUpdatedAt > 0 && <ContextHelp ariaLabel={`${title}更新时间`} content={`更新于 ${dateTime(query.dataUpdatedAt)}`} />}</div></header>
 {query.error && <InlineFeedback tone="error" label={title + '刷新失败'} detail={query.error.message}
   action={<Button type="link" size="small" onClick={() => void query.refetch()}>重试</Button>} />}
 {query.isPending ? <Skeleton active paragraph={{ rows: 4 }} /> : children}
</section>;

