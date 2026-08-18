import {
  ApiOutlined,
  AppstoreOutlined,
  ArrowRightOutlined,
  BankOutlined,
  BarChartOutlined,
  BookOutlined,
  DatabaseOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  GlobalOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Alert, Button, Empty, Input, Skeleton, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { AssetPortalHeader } from '../components/AssetPortalHeader';
import { useAssetPortalAssets, useAssetPortalOverview } from '../hooks/useAssetPortal';
import {
  assetSensitivityLevelLabels,
  assetSyncStatusLabels,
  assetTypeLabels,
  type AssetPortalAssetSummary,
  type AssetPortalFilters,
  type AssetSensitivityLevel,
  type AssetSyncStatus,
  type AssetType,
} from '../model/asset';
import './assetPortal.css';

const assetTypeIcons: Record<AssetType, ReactNode> = {
  DATA_MODEL: <DatabaseOutlined />,
  FILE_DATASET: <FileTextOutlined />,
  DATA_SERVICE: <ApiOutlined />,
  DICTIONARY: <BookOutlined />,
};

const domainVisuals: Array<{ icon: ReactNode; tone: string }> = [
  { icon: <BankOutlined />, tone: 'blue' },
  { icon: <EnvironmentOutlined />, tone: 'green' },
  { icon: <BarChartOutlined />, tone: 'orange' },
  { icon: <GlobalOutlined />, tone: 'purple' },
  { icon: <TeamOutlined />, tone: 'cyan' },
  { icon: <AppstoreOutlined />, tone: 'indigo' },
];

const sensitivityClass = (level: AssetSensitivityLevel) => `asset-portal-sensitivity asset-portal-sensitivity-${level.toLocaleLowerCase()}`;
const syncClass = (status: AssetSyncStatus) => `asset-portal-sync asset-portal-sync-${status.toLocaleLowerCase()}`;

export const AssetPortalPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchDraft, setSearchDraft] = useState('');
  const [filters, setFilters] = useState<AssetPortalFilters>({});
  const overviewQuery = useAssetPortalOverview();
  const assetsQuery = useAssetPortalAssets(filters);
  const overview = overviewQuery.data;
  const pages = assetsQuery.data?.pages;
  const assets = useMemo(() => {
    const unique = new Map<string, AssetPortalAssetSummary>();
    pages?.forEach((page) => page.content.forEach((asset) => unique.set(asset.id, asset)));
    return [...unique.values()];
  }, [pages]);
  const totalElements = assetsQuery.data?.pages[0]?.totalElements ?? 0;
  const activeDomain = overview?.domains.find((domain) => domain.id === filters.directoryId);

  const scrollToDiscovery = () => document.getElementById('asset-portal-discovery')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  const applyFilters = (next: AssetPortalFilters) => {
    setFilters(next);
    window.setTimeout(scrollToDiscovery, 0);
  };
  const applySearch = (keyword: string) => {
    const normalized = keyword.trim();
    applyFilters({ ...filters, keyword: normalized || undefined });
  };
  const clearFilters = () => {
    setSearchDraft('');
    setFilters({});
  };

  useEffect(() => {
    if (!location.hash) return;
    const id = location.hash === '#categories' ? 'asset-portal-categories' : location.hash === '#discovery' ? 'asset-portal-discovery' : undefined;
    if (!id) return;
    window.setTimeout(() => document.getElementById(id)?.scrollIntoView({ block: 'start' }), 0);
  }, [location.hash]);

  return (
    <div className="asset-portal-page">
      <AssetPortalHeader page="home" />

      <main>
        <section className="asset-portal-hero">
          <div className="asset-portal-hero-glow asset-portal-hero-glow-left" />
          <div className="asset-portal-hero-glow asset-portal-hero-glow-right" />
          <div className="asset-portal-container asset-portal-hero-content">
            <span className="asset-portal-eyebrow"><SafetyCertificateOutlined /> 可信数据 · 统一发现 · 权限内使用</span>
            <Typography.Title level={1}>让每一项数据都可发现、可理解、可使用</Typography.Title>
            <Typography.Paragraph>汇聚已发布的数据模型、文件数据集、码表和数据服务，为业务人员提供统一的资产发现入口。</Typography.Paragraph>
            <Input.Search
              value={searchDraft}
              allowClear
              enterButton={<><SearchOutlined /> 搜索资产</>}
              size="large"
              className="asset-portal-search"
              placeholder="搜索资产名称、编码、业务说明或标签"
              aria-label="搜索数据资产"
              onChange={(event) => setSearchDraft(event.target.value)}
              onSearch={applySearch}
            />
            {overview && overview.popularTags.length > 0 ? (
              <div className="asset-portal-hot-keywords">
                <span>热门标签</span>
                {overview.popularTags.map((keyword) => (
                  <button key={keyword} type="button" onClick={() => { setSearchDraft(keyword); applySearch(keyword); }}>{keyword}</button>
                ))}
              </div>
            ) : null}
            <div className="asset-portal-statistics" aria-label="已发布资产概览">
              <div><strong>{overviewQuery.isPending ? '—' : overview?.totalCount ?? 0}</strong><span>已发布资产</span></div>
              {(Object.keys(assetTypeLabels) as AssetType[]).map((type) => (
                <span className="asset-portal-statistic-item" key={type}>
                  <i />
                  <span><strong>{overviewQuery.isPending ? '—' : overview?.typeCounts[type] ?? 0}</strong><span>{assetTypeLabels[type]}</span></span>
                </span>
              ))}
            </div>
          </div>
        </section>

        <section className="asset-portal-section asset-portal-category-section" id="asset-portal-categories">
          <div className="asset-portal-container">
            <div className="asset-portal-section-heading">
              <div><span className="asset-portal-section-kicker">BUSINESS DOMAIN</span><Typography.Title level={2}>按业务领域发现数据</Typography.Title><Typography.Paragraph>从业务领域出发，浏览该领域及其下级领域已经发布的数据资产。</Typography.Paragraph></div>
              <Button type="link" onClick={() => { applyFilters({ ...filters, directoryId: undefined }); }}>浏览全部资产 <ArrowRightOutlined /></Button>
            </div>
            {overviewQuery.isPending ? (
              <div className="asset-portal-category-grid">{[0, 1, 2].map((item) => <Skeleton.Node active key={item} className="asset-portal-category-skeleton" />)}</div>
            ) : overviewQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="业务领域加载失败"
                description={overviewQuery.error instanceof ApiError ? overviewQuery.error.message : '请稍后重试。'}
                action={<Button icon={<ReloadOutlined />} onClick={() => void overviewQuery.refetch()}>重试</Button>}
              />
            ) : overview?.domains.length ? (
              <div className="asset-portal-category-grid">
                {overview.domains.map((domain, index) => {
                  const visual = domainVisuals[index % domainVisuals.length];
                  return (
                    <button key={domain.id} type="button" className={`asset-portal-category-card ${filters.directoryId === domain.id ? 'active' : ''}`} onClick={() => applyFilters({ ...filters, directoryId: domain.id })}>
                      <span className={`asset-portal-category-icon asset-portal-category-icon-${visual.tone}`}>{visual.icon}</span>
                      <span className="asset-portal-category-main"><strong>{domain.name}</strong><span>{domain.description || '该领域暂未填写说明'}</span></span>
                      <span className="asset-portal-category-count">{domain.assetCount}<small>项资产</small></span>
                      <ArrowRightOutlined className="asset-portal-category-arrow" />
                    </button>
                  );
                })}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未维护顶级业务领域" />
            )}
          </div>
        </section>

        <section className="asset-portal-section asset-portal-discovery-section" id="asset-portal-discovery">
          <div className="asset-portal-container">
            <div className="asset-portal-section-heading asset-portal-discovery-heading">
              <div>
                <span className="asset-portal-section-kicker">PUBLISHED ASSETS</span>
                <Typography.Title level={2}>{filters.keyword ? `“${filters.keyword}”的搜索结果` : activeDomain ? `${activeDomain.name}资产` : '已发布数据资产'}</Typography.Title>
                <Typography.Paragraph>{assetsQuery.isPending ? '正在读取已发布资产…' : `共找到 ${totalElements} 项资产，推荐资产优先展示`}</Typography.Paragraph>
              </div>
              <div className="asset-portal-type-filters" aria-label="按资产类型筛选">
                <button type="button" className={!filters.assetType ? 'active' : ''} onClick={() => applyFilters({ ...filters, assetType: undefined })}>全部</button>
                {(Object.keys(assetTypeLabels) as AssetType[]).map((type) => <button key={type} type="button" className={filters.assetType === type ? 'active' : ''} onClick={() => applyFilters({ ...filters, assetType: type })}>{assetTypeLabels[type]}</button>)}
              </div>
            </div>

            {assetsQuery.isPending ? (
              <div className="asset-portal-asset-grid">{[0, 1, 2].map((item) => <Skeleton active key={item} paragraph={{ rows: 4 }} className="asset-portal-asset-skeleton" />)}</div>
            ) : assetsQuery.isError ? (
              <div className="asset-portal-empty">
                <SearchOutlined />
                <strong>资产加载失败</strong>
                <span>{assetsQuery.error instanceof ApiError ? assetsQuery.error.message : '请稍后重试。'}</span>
                <Button type="primary" icon={<ReloadOutlined />} onClick={() => void assetsQuery.refetch()}>重新加载</Button>
              </div>
            ) : assets.length > 0 ? (
              <>
                <div className="asset-portal-asset-grid">
                  {assets.map((asset) => (
                    <article key={asset.id} className="asset-portal-asset-card">
                      <div className="asset-portal-asset-card-top">
                        <span className={`asset-portal-asset-type asset-portal-asset-type-${asset.assetType.toLocaleLowerCase()}`}>{assetTypeIcons[asset.assetType]}</span>
                        <div className="asset-portal-asset-tags">
                          <Tag>{assetTypeLabels[asset.assetType]}</Tag>
                          {asset.featured ? <Tag color="purple">推荐</Tag> : null}
                        </div>
                        <span className={sensitivityClass(asset.sensitivityLevel)}>{assetSensitivityLevelLabels[asset.sensitivityLevel]}</span>
                      </div>
                      <button type="button" className="asset-portal-asset-title" onClick={() => navigate(`/assets/${asset.id}`)}>{asset.name}</button>
                      <div className="asset-portal-asset-code">{asset.code || '—'}</div>
                      <p>{asset.summary || '该资产暂未填写简介。'}</p>
                      {asset.tags.length > 0 ? <div className="asset-portal-asset-keywords">{asset.tags.map((tag) => <span key={tag}>#{tag}</span>)}</div> : null}
                      <div className="asset-portal-card-status-row"><span className={syncClass(asset.syncStatus)}>{assetSyncStatusLabels[asset.syncStatus]}</span></div>
                      <footer><span>{asset.directoryPath || '未设置业务领域'}</span><span>{[asset.ownerName, asset.updateFrequency].filter(Boolean).join(' · ') || '治理信息待完善'}</span></footer>
                    </article>
                  ))}
                </div>
                {assetsQuery.hasNextPage ? (
                  <div className="asset-portal-load-more">
                    <Button size="large" loading={assetsQuery.isFetchingNextPage} onClick={() => void assetsQuery.fetchNextPage()}>加载更多资产</Button>
                    <span>已展示 {assets.length} / {totalElements} 项</span>
                  </div>
                ) : null}
              </>
            ) : (
              <div className="asset-portal-empty">
                <SearchOutlined />
                <strong>{overview?.totalCount === 0 ? '暂无已发布数据资产' : '没有匹配的数据资产'}</strong>
                <span>{overview?.totalCount === 0 ? '管理员发布资产后将在这里展示。' : '请尝试更换关键词或清除筛选条件。'}</span>
                {Object.keys(filters).length > 0 ? <Button onClick={clearFilters}>清除筛选</Button> : null}
              </div>
            )}
          </div>
        </section>

        <section className="asset-portal-section asset-portal-guide-section">
          <div className="asset-portal-container asset-portal-guide-inner">
            <div><span className="asset-portal-section-kicker">HOW TO USE</span><Typography.Title level={2}>从发现到使用，边界清晰</Typography.Title><Typography.Paragraph>门户提供公开的安全元数据；进入原资源继续遵循相应模块的登录和查看权限。</Typography.Paragraph></div>
            <div className="asset-portal-guide-steps">
              <div><span>01</span><strong>发现资产</strong><p>通过真实业务领域、类型、标签和关键词定位资产。</p></div>
              <ArrowRightOutlined />
              <div><span>02</span><strong>理解资产</strong><p>查看治理信息、来源状态和经过裁剪的安全元数据。</p></div>
              <ArrowRightOutlined />
              <div><span>03</span><strong>权限内使用</strong><p>登录后，经来源模块权限校验进入已有资源能力。</p></div>
            </div>
          </div>
        </section>
      </main>

      <footer className="asset-portal-footer">
        <div className="asset-portal-container"><span>DataScalpel 数据资产门户</span><span>统一发现 · 可信治理 · 安全使用</span></div>
      </footer>
    </div>
  );
};
