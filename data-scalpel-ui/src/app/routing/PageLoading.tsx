import { LoadingOutlined } from '@ant-design/icons';
import { Skeleton } from 'antd';
import './page-loading.css';

interface PageLoadingProps {
  label: string;
  layout?: 'table' | 'detail' | 'overview';
  fullPage?: boolean;
}

/** Eager, lightweight route fallback; the application shell stays interactive. */
export const PageLoading = ({ label, layout = 'table', fullPage = false }: PageLoadingProps) => {
  const content = <section className={`page-loading page-loading-layout-${layout}`} aria-busy="true" aria-label={label}>
    <div className="page-loading-status" role="status"><LoadingOutlined aria-hidden spin /><span>{label}</span></div>
    <div className="page-loading-structure" aria-hidden="true">
      <div className="page-loading-toolbar">
        <Skeleton.Input active block />
        <Skeleton.Input active block />
        <Skeleton.Button active />
      </div>
      {layout === 'table' ? <div className="page-loading-results">
        <div className="page-loading-result-heading"><Skeleton.Input active size="small" /></div>
        <div className="page-loading-table">
          {Array.from({ length: 4 }, (_, index) => <Skeleton key={index} active title={{ width: '55%' }} paragraph={{ rows: 6, width: index === 0 ? ['75%', '90%', '60%', '85%', '70%', '80%'] : '70%' }} />)}
        </div>
        <div className="page-loading-pagination"><Skeleton.Input active size="small" /></div>
      </div> : <div className="page-loading-panels">
        <div className="page-loading-panel"><Skeleton active title={{ width: '30%' }} paragraph={{ rows: 5, width: ['85%', '65%', '90%', '70%', '80%'] }} /></div>
        <div className="page-loading-panel"><Skeleton active title={{ width: '45%' }} paragraph={{ rows: 4, width: ['90%', '75%', '85%', '65%'] }} /></div>
        {layout === 'overview' && <div className="page-loading-panel"><Skeleton active title={{ width: '40%' }} paragraph={{ rows: 4 }} /></div>}
      </div>}
    </div>
  </section>;

  return fullPage ? <main className="application-loading">
    <div className="application-loading-brand"><img src="/data-scalpel-mark.svg" alt="" /><strong>DataScalpel</strong><span>数据治理工作台</span></div>
    <div className="application-loading-content">{content}</div>
  </main> : content;
};
