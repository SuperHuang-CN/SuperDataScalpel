import { ApiOutlined, ArrowRightOutlined, BookOutlined, CameraOutlined, DatabaseOutlined, FileTextOutlined } from '@ant-design/icons';
import { Link, useLocation } from 'react-router-dom';
import { assetSensitivityLevelLabels, assetTypeLabels, type AssetPortalAssetSummary, type AssetType } from '../model/asset';

const icons = { DATA_MODEL: DatabaseOutlined, FILE_DATASET: FileTextOutlined, PANORAMA: CameraOutlined, DICTIONARY: BookOutlined, DATA_SERVICE: ApiOutlined };
export const AssetPortalTypeIcon = ({ type }: { type: AssetType }) => {
  const Icon = icons[type];
  return <Icon />;
};

export const AssetPortalResourceCard = ({ asset, variant = 'catalog' }: { asset: AssetPortalAssetSummary; variant?: 'catalog' | 'spotlight' | 'compact' }) => {
  const location = useLocation();
  const target = `/assets/${asset.id}`;
  const returnTo = `${location.pathname}${location.search}`;
  return <article className={`portal-resource portal-resource-${variant}`}>
    {variant === 'spotlight' && <div className="portal-resource-art" aria-hidden="true"><AssetPortalTypeIcon type={asset.assetType} /></div>}
    <div className="portal-resource-body">
      <div className="portal-resource-meta"><AssetPortalTypeIcon type={asset.assetType} /><span>{assetTypeLabels[asset.assetType]}</span><span className={`portal-level portal-level-${asset.sensitivityLevel.toLowerCase()}`}>{assetSensitivityLevelLabels[asset.sensitivityLevel]}</span></div>
      <h3><Link to={target} state={{ returnTo }}>{asset.name}</Link></h3>
      {variant === 'catalog' && <div className="portal-resource-code">{asset.code || '—'}</div>}
      <p className="portal-resource-summary">{asset.summary || '暂无资源说明'}</p>
      {variant === 'catalog' && <><div className="portal-tags">{asset.tags.map(tag => <span key={tag}>#{tag}</span>)}</div><dl className="portal-card-facts"><div><dt>负责人</dt><dd>{asset.ownerName || '—'}</dd></div><div><dt>更新频率</dt><dd>{asset.updateFrequency || '—'}</dd></div></dl></>}
      <footer><span>{asset.directoryPath || '未设置领域'}{variant !== 'catalog' && asset.updateFrequency ? ` · ${asset.updateFrequency}` : ''}</span><Link to={target} state={{ returnTo }} aria-label={`查看${asset.name}`}>{variant === 'compact' ? '' : '查看详情'} <ArrowRightOutlined /></Link></footer>
    </div>
  </article>;
};
