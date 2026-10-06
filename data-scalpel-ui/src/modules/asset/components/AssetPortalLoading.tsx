import { LoadingOutlined } from '@ant-design/icons';
import { Skeleton } from 'antd';
import { AssetPortalHeader } from './AssetPortalHeader';
import '../pages/assetPortal.css';
import '../pages/assetPortalCustomer.css';
import './assetPortalLoading.css';

interface AssetPortalLoadingProps {
  page: 'home' | 'catalog' | 'detail';
}

const ResourceSkeletons = () => <div className="portal-loading-resources">
  {Array.from({ length: 3 }, (_, index) => <div key={index} className="portal-loading-resource">
    <Skeleton active title={{ width: '65%' }} paragraph={{ rows: 3, width: ['95%', '80%', '50%'] }} />
  </div>)}
</div>;

/** Loaded with the router so the portal remains recognisable before page code arrives. */
export const AssetPortalLoading = ({ page }: AssetPortalLoadingProps) => {
  const label = { home: '正在加载数据资产门户…', catalog: '正在加载资产目录…', detail: '正在加载资产详情…' }[page];
  return <div className={`asset-portal-page portal-customer asset-portal-loading asset-portal-loading-${page}`}>
    <AssetPortalHeader page={page} />
    <main aria-busy="true" aria-label={label}>
      <section className="portal-loading-intro">
        <div className="asset-portal-container">
          <div className="portal-loading-status" role="status"><LoadingOutlined spin aria-hidden /><span>{label}</span></div>
          <div className="portal-loading-intro-content" aria-hidden="true">
            <Skeleton active title={{ width: page === 'home' ? '70%' : '55%' }} paragraph={{ rows: page === 'home' ? 3 : 2, width: ['95%', '75%', '50%'] }} />
            {page !== 'detail' && <Skeleton.Input active block size="large" />}
          </div>
        </div>
      </section>
      <div className="asset-portal-container portal-loading-body" aria-hidden="true">
        {page === 'home' ? <>
          <div className="portal-loading-statistics">{Array.from({ length: 6 }, (_, index) => <Skeleton key={index} active title={{ width: '55%' }} paragraph={{ rows: 1, width: '75%' }} />)}</div>
          <div className="portal-loading-section-heading"><Skeleton.Input active /></div>
          <ResourceSkeletons />
        </> : page === 'catalog' ? <div className="portal-loading-catalog">
          <aside className="portal-loading-sidebar"><Skeleton active paragraph={{ rows: 5, width: ['80%', '65%', '90%', '70%', '60%'] }} /></aside>
          <div><div className="portal-loading-section-heading"><Skeleton.Input active /></div><ResourceSkeletons /></div>
        </div> : <div className="portal-loading-detail">
          <div className="portal-loading-resource"><Skeleton active title={{ width: '25%' }} paragraph={{ rows: 7, width: ['85%', '70%', '90%', '65%', '80%', '70%', '50%'] }} /></div>
          <aside className="portal-loading-resource"><Skeleton active title={{ width: '55%' }} paragraph={{ rows: 4 }} /></aside>
        </div>}
      </div>
    </main>
  </div>;
};
