import { ArrowRightOutlined, GlobalOutlined, SearchOutlined, FileTextOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AssetPortalHeader } from '../components/AssetPortalHeader';
import { AssetPortalSearch } from '../components/AssetPortalSearch';
import { AssetPortalResourceCard, AssetPortalTypeIcon } from '../components/AssetPortalResourceCard';
import { AssetPortalCarousel } from '../components/AssetPortalCarousel';
import { AssetPortalFeedback, AssetPortalFooter } from '../components/AssetPortalFeedback';
import { useAssetPortalAssets, useAssetPortalOverview } from '../hooks/useAssetPortal';
import { assetTypeLabels, type AssetType } from '../model/asset';
import { assetCatalogUrl } from '../model/assetPortalNavigation';
import heroImage from '../assets/portal-city-hero.png';
import './assetPortal.css';
import './assetPortalCustomer.css';

const typeDescriptions: Record<AssetType, string> = { DATA_MODEL: '结构化数据与业务模型', FILE_DATASET: '文件资源与空间数据', PANORAMA: '全景影像与现场实景', DICTIONARY: '统一编码与标准参考', DATA_SERVICE: '面向应用的数据接口' };

export const AssetPortalPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const overview = useAssetPortalOverview();
  const featured = useAssetPortalAssets({ featured: true, sort: 'CREATED' }, 0, 9);
  const recent = useAssetPortalAssets({ sort: 'LATEST' }, 0, 12);
  useEffect(() => {
    if (location.hash === '#discovery') { navigate('/assets/browse', { replace: true }); return; }
    const id = location.hash === '#categories' ? 'asset-portal-categories' : location.hash === '#guide' ? 'asset-portal-guide' : undefined;
    if (id) document.getElementById(id)?.scrollIntoView({ block: 'start' });
    else window.scrollTo({ top: 0, behavior: 'instant' });
  }, [location.hash, navigate]);
  const picked = featured.data?.content ?? [];
  const latest = recent.data?.content ?? [];
  const featuredPages = Array.from({ length: Math.ceil(picked.length / 3) }, (_, i) => <div className="portal-feature-group">
    <AssetPortalResourceCard asset={picked[i * 3]} variant="spotlight" />
    {picked.slice(i * 3 + 1, i * 3 + 3).length > 0 && <div className="portal-feature-stack">{picked.slice(i * 3 + 1, i * 3 + 3).map(asset => <AssetPortalResourceCard key={asset.id} asset={asset} variant="compact" />)}</div>}
  </div>);
  const recentPages = Array.from({ length: Math.ceil(latest.length / 4) }, (_, i) => <div className="portal-recent-group">{latest.slice(i * 4, i * 4 + 4).map(asset => <Link className="portal-recent-item" key={asset.id} to={`/assets/${asset.id}`} state={{ returnTo: '/assets' }}><strong>{asset.name}</strong><span>{assetTypeLabels[asset.assetType]}<time dateTime={asset.publishedAt}>{new Date(asset.publishedAt).toLocaleDateString('zh-CN')}</time></span></Link>)}</div>);
  const count = (value: number | undefined) => overview.isPending || overview.isError ? '—' : (value ?? 0).toLocaleString('zh-CN');
  return <div className="asset-portal-page portal-customer">
    <AssetPortalHeader page="home" />
    <main>
      <section className="portal-hero"><img src={heroImage} alt="城市与河流构成的空间数据概念模型" fetchPriority="high" /><div className="asset-portal-container portal-hero-content"><h1>发现数据的<br /><span>更多可能</span></h1><p>从业务数据到空间实景，找到您需要的资源，<br />让每一次探索，都成为业务的新起点。</p><AssetPortalSearch onSearch={keyword => navigate(assetCatalogUrl({ keyword }))} />{Boolean(overview.data?.popularTags.length) && <div className="portal-hot"><span>热门标签</span>{overview.data?.popularTags.slice(0, 5).map(tag => <Link key={tag} to={assetCatalogUrl({ keyword: tag })}>{tag}</Link>)}</div>}</div></section>
      <section className="asset-portal-container portal-statistics" aria-label="已发布资产统计"><div className="portal-stat-total"><span>已发布资产</span><strong>{count(overview.data?.totalCount)}<small>项</small></strong><span>持续汇聚的数据资源</span></div>{(Object.keys(assetTypeLabels) as AssetType[]).map(type => <Link key={type} to={assetCatalogUrl({ assetType: type })}><div><AssetPortalTypeIcon type={type} /><ArrowRightOutlined /></div><h2>{assetTypeLabels[type]}<strong>{count(overview.data?.typeCounts[type])}</strong></h2><p>{typeDescriptions[type]}</p></Link>)}</section>
      <section className="portal-section" id="asset-portal-categories"><div className="asset-portal-container portal-domains"><div><h2>从您的业务出发</h2><p>按领域聚合资源，<br />让需要的数据更容易找到。</p><Link to="/assets/browse">浏览全部资产 <ArrowRightOutlined /></Link></div>{overview.data && !overview.isError && overview.data.domains.length ? <div className="portal-domain-grid">{overview.data.domains.map(domain => <Link key={domain.id} to={assetCatalogUrl({ directoryId: domain.id })}><GlobalOutlined /><span><strong>{domain.name}</strong><small>{domain.assetCount.toLocaleString('zh-CN')} 项资产</small></span><ArrowRightOutlined /></Link>)}</div> : <AssetPortalFeedback pending={overview.isPending} error={overview.error} empty="尚未维护业务领域" retry={() => void overview.refetch()} />}</div></section>
      <section className="portal-section portal-discovery"><div className="asset-portal-container"><div className="portal-section-heading"><div><h2>值得发现的数据资源</h2><p>从精选资源开始，发现数据在业务中的更多价值。</p></div><Link to="/assets/browse">查看全部 <ArrowRightOutlined /></Link></div><div className="portal-discovery-grid"><div>{featured.isPending || featured.isError || !picked.length ? <AssetPortalFeedback pending={featured.isPending} error={featured.error} empty="暂无推荐资产，您可以浏览全部已发布资源" retry={() => void featured.refetch()} /> : <AssetPortalCarousel key={picked.map(a => a.id).join(',')} pages={featuredPages} label="精选资源" interval={6000} total={picked.length} />}</div><aside className="portal-recent"><div className="portal-recent-heading"><h3>最近发布</h3><span>按发布时间</span></div>{recent.isPending || recent.isError || !latest.length ? <AssetPortalFeedback pending={recent.isPending} error={recent.error} retry={() => void recent.refetch()} /> : <AssetPortalCarousel key={latest.map(a => a.id).join(',')} pages={recentPages} label="最近发布" interval={4000} total={latest.length} vertical />}<Link className="portal-more" to={assetCatalogUrl({ sort: 'LATEST' })}>查看发布动态 <ArrowRightOutlined /></Link></aside></div></div></section>
      <section className="portal-guide portal-section" id="asset-portal-guide"><div className="asset-portal-container"><div><h2>让数据走进业务</h2><p>从发现资源，到开始使用。</p></div><div><SearchOutlined /><span><h3>找到所需资源</h3><p>按关键词、领域或类型，快速定位数据。</p></span></div><div><FileTextOutlined /><span><h3>了解数据内容</h3><p>查看资源说明、更新频率与负责人。</p></span></div><div><SafetyCertificateOutlined /><span><h3>登录后使用</h3><p>在资产详情页，按账号权限访问资源。</p></span></div></div></section>
    </main><AssetPortalFooter />
  </div>;
};
