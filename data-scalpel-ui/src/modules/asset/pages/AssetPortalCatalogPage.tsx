import { Button, Pagination, Select } from 'antd';
import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AssetPortalHeader } from '../components/AssetPortalHeader';
import { AssetPortalSearch } from '../components/AssetPortalSearch';
import { AssetPortalResourceCard } from '../components/AssetPortalResourceCard';
import { AssetPortalFeedback, AssetPortalFooter } from '../components/AssetPortalFeedback';
import { useAssetPortalAssets, useAssetPortalOverview } from '../hooks/useAssetPortal';
import { assetTypeLabels, type AssetPortalFilters, type AssetType } from '../model/asset';
import './assetPortal.css';
import './assetPortalCustomer.css';

export const AssetPortalCatalogPage = () => {
  const [params, setParams] = useSearchParams();
  const rawType = params.get('assetType');
  const filters: AssetPortalFilters = { keyword: params.get('keyword') || undefined, directoryId: params.get('directoryId') || undefined, assetType: rawType && Object.hasOwn(assetTypeLabels, rawType) ? rawType as AssetType : undefined, sort: params.get('sort') === 'LATEST' ? 'LATEST' : 'RECOMMENDED' };
  const rawPage = Number(params.get('page') || 0);
  const page = Number.isSafeInteger(rawPage) && rawPage >= 0 ? rawPage : 0;
  const rawSize = Number(params.get('size') || 12);
  const size = [12, 24, 48].includes(rawSize) ? rawSize : 12;
  const overview = useAssetPortalOverview();
  const assets = useAssetPortalAssets(filters, page, size);
  const change = (values: Record<string, string | undefined>, resetPage = true) => { const next = new URLSearchParams(params); Object.entries(values).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key)); if (resetPage) next.delete('page'); setParams(next); };
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [params]);
  const filtered = Boolean(filters.keyword || filters.assetType || filters.directoryId);
  return <div className="asset-portal-page portal-customer"><AssetPortalHeader page="catalog" /><main>
    <section className="portal-catalog-top"><div className="asset-portal-container"><div><h1>探索数据资产</h1><p>理解数据，选择资源，让业务更进一步。</p></div><AssetPortalSearch key={filters.keyword || ''} initialValue={filters.keyword} onSearch={keyword => change({ keyword })} /></div></section>
    <div className="asset-portal-container portal-catalog-layout"><aside className="portal-catalog-sidebar"><h2>业务领域</h2><button type="button" aria-pressed={!filters.directoryId} onClick={() => change({ directoryId: undefined })}>全部领域 <span>{overview.data?.totalCount ?? '—'}</span></button>{overview.data?.domains.map(domain => <button type="button" key={domain.id} aria-pressed={filters.directoryId === domain.id} onClick={() => change({ directoryId: domain.id })}>{domain.name}<span>{domain.assetCount}</span></button>)}{overview.isError && <AssetPortalFeedback error={overview.error} retry={() => void overview.refetch()} />}<p>门户展示已发布资产。<br />实际使用范围以您的账号权限为准。</p></aside><section className="portal-catalog-results"><div className="portal-type-tabs" aria-label="资源类型"><button type="button" aria-pressed={!filters.assetType} onClick={() => change({ assetType: undefined })}>全部资产</button>{(Object.keys(assetTypeLabels) as AssetType[]).map(type => <button type="button" key={type} aria-pressed={filters.assetType === type} onClick={() => change({ assetType: type })}>{assetTypeLabels[type]}</button>)}</div><div className="portal-result-heading"><span aria-live="polite">{assets.isPending ? '正在查找资产…' : assets.isError ? '资产查询失败' : <>找到 <strong>{assets.data?.totalElements ?? 0}</strong> 项资产</>}</span><Select aria-label="资产排序" value={filters.sort} onChange={sort => change({ sort })} options={[{ value: 'RECOMMENDED', label: '推荐优先' }, { value: 'LATEST', label: '最近发布' }]} /></div>{filtered && <div className="portal-applied"><span>当前筛选：{[filters.keyword, filters.assetType ? assetTypeLabels[filters.assetType] : '', filters.directoryId ? overview.data?.domains.find(d => d.id === filters.directoryId)?.name || '所选领域' : ''].filter(Boolean).join(' / ')}</span><Button type="link" onClick={() => setParams({})}>清除筛选</Button></div>}
      {assets.isPending || assets.isError || !assets.data?.content.length ? <><AssetPortalFeedback pending={assets.isPending} error={assets.error} empty={page > 0 ? '此页暂无资产，请返回第一页' : filtered ? '没有找到匹配的资产，请调整或清除筛选条件' : '暂无已发布资产'} retry={() => void assets.refetch()} />{page > 0 && !assets.isPending && !assets.isError && <Button onClick={() => change({ page: undefined })}>返回第一页</Button>}</> : <><div className="portal-catalog-grid">{assets.data.content.map(asset => <AssetPortalResourceCard key={asset.id} asset={asset} />)}</div><Pagination className="portal-pagination" current={page + 1} pageSize={size} total={assets.data.totalElements} pageSizeOptions={[12, 24, 48]} showSizeChanger onChange={(nextPage, nextSize) => change({ page: String(nextSize === size ? nextPage - 1 : 0), size: String(nextSize) }, false)} showTotal={total => `共 ${total} 项`} /></>}
    </section></div></main><AssetPortalFooter /></div>;
};
