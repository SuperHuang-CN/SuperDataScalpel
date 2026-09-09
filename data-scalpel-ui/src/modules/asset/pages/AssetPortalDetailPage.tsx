import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  ApiOutlined,
  ArrowLeftOutlined,
  BookOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  CameraOutlined,
  LoginOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { Button, Descriptions, Result, Skeleton, Space, Tag, Typography, message } from 'antd';
import type { ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError, hasAccessToken } from '../../../shared/api/http';
import { AssetPortalHeader } from '../components/AssetPortalHeader';
import { useAssetPortalDetail, useAssetSourceNavigation } from '../hooks/useAssetPortal';
import {
  assetPortalSourceDisplayStatusLabels,
  assetSensitivityLevelLabels,
  assetSyncStatusLabels,
  assetTypeLabels,
  type AssetPortalAssetDetail,
  type AssetPortalSourceDisplayStatus,
  type AssetSensitivityLevel,
  type AssetType,
} from '../model/asset';
import './assetPortal.css';

const assetTypeIcons: Record<AssetType, ReactNode> = {
  DATA_MODEL: <DatabaseOutlined />,
  FILE_DATASET: <FileTextOutlined />,
  PANORAMA: <CameraOutlined />,
  DATA_SERVICE: <ApiOutlined />,
  DICTIONARY: <BookOutlined />,
};

const metadataLabels: Record<AssetType, Record<string, string>> = {
  DATA_MODEL: {
    status: '模型状态',
    warehouseLayerCode: '数仓分层编码',
    warehouseLayerName: '数仓分层',
    schemaVersion: 'Schema 版本',
    fieldCount: '字段数量',
  },
  PANORAMA: { contentVersion: '内容版本', width: '宽度（像素）', height: '高度（像素）', byteSize: '原图字节数', captureTime: '拍摄时间', captureOffset: '时区偏移', manufacturer: '相机厂商', cameraModel: '相机型号', hasLocation: '是否有位置' },
  FILE_DATASET: {
    datasetType: '数据集类型',
    fileCount: '文件数量',
    tableCount: '数据表数量',
    readyTableCount: '可用表数量',
  },
  DICTIONARY: {
    enabled: '启用状态',
    valueType: '取值类型',
    contentVersion: '内容版本',
    itemCount: '码值数量',
  },
  DATA_SERVICE: {
    serviceType: '服务类型',
    accessMode: '访问模式',
    routePath: '服务路由',
    definitionVersion: '定义版本',
    deploymentStatus: '部署状态',
  },
};

const sourceStatusTone: Record<AssetPortalSourceDisplayStatus, 'success' | 'warning' | 'processing'> = {
  AVAILABLE: 'success',
  UNAVAILABLE: 'warning',
  CACHED: 'processing',
};

const sensitivityClass = (level: AssetSensitivityLevel) => `asset-portal-sensitivity asset-portal-sensitivity-${level.toLocaleLowerCase()}`;

const displayValue = (value: unknown): ReactNode => {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  return JSON.stringify(value);
};

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
};

const sourceStatusDescription = (status: AssetPortalSourceDisplayStatus): string => {
  if (status === 'AVAILABLE') return '以下信息实时读取自来源资源，并经过公开安全字段裁剪。';
  if (status === 'UNAVAILABLE') return '来源资源仍存在，但当前状态不满足可用条件；以下为实时安全元数据。';
  return '当前无法读取来源资源，以下展示资产最后一次成功同步的安全快照。';
};

const MetadataPanel = ({ asset }: { asset: AssetPortalAssetDetail }) => {
  const labels = metadataLabels[asset.assetType];
  const entries = Object.entries(labels);
  return (
    <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} bordered size="middle">
      {entries.map(([key, label]) => (
        <Descriptions.Item key={key} label={label}>{displayValue(asset.metadata[key])}</Descriptions.Item>
      ))}
    </Descriptions>
  );
};

export const AssetPortalDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const [messageApi, messageContext] = message.useMessage();
  const detailQuery = useAssetPortalDetail(id);
  const sourceNavigationMutation = useAssetSourceNavigation();

  const enterSourceAsset = async () => {
    if (!id) return;
    if (!hasAccessToken()) {
      navigate('/login', { state: { from: location } });
      return;
    }
    try {
      const navigation = await sourceNavigationMutation.mutateAsync(id);
      navigate(navigation.path, {
        state: {
          returnTo: `${location.pathname}${location.search}${location.hash}`,
          returnLabel: '返回数据资产门户',
        },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        navigate('/login', { state: { from: location } });
        return;
      }
      messageApi.error(error instanceof ApiError ? error.message : '暂时无法进入来源资源');
    }
  };

  if (!id) {
    return (
      <div className="asset-portal-page asset-portal-detail-page">
        <AssetPortalHeader page="detail" />
        <Result status="404" title="资产地址无效" extra={<Button type="primary" onClick={() => navigate('/assets')}>返回资产门户</Button>} />
      </div>
    );
  }

  if (detailQuery.isPending) {
    return (
      <div className="asset-portal-page asset-portal-detail-page">
        <AssetPortalHeader page="detail" />
        <main className="asset-portal-container asset-portal-detail-loading"><Skeleton active paragraph={{ rows: 10 }} /></main>
      </div>
    );
  }

  if (!detailQuery.data || detailQuery.isError) {
    const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404;
    return (
      <div className="asset-portal-page asset-portal-detail-page">
        <AssetPortalHeader page="detail" />
        <Result
          status={notFound ? '404' : 'error'}
          title={notFound ? '资产不存在或已下线' : '资产详情加载失败'}
          subTitle={notFound ? '只有仍处于已发布状态的资产可以从门户访问。' : detailQuery.error instanceof ApiError ? detailQuery.error.message : '请稍后重试。'}
          extra={(
            <Space>
              <Button onClick={() => navigate('/assets')}>返回资产门户</Button>
              {!notFound ? <Button type="primary" icon={<ReloadOutlined />} onClick={() => void detailQuery.refetch()}>重试</Button> : null}
            </Space>
          )}
        />
      </div>
    );
  }

  const asset = detailQuery.data;
  return (
    <div className="asset-portal-page asset-portal-detail-page">
      {messageContext}
      <AssetPortalHeader page="detail" />
      <main>
        <section className="asset-portal-detail-hero">
          <div className="asset-portal-container">
            <Button type="text" icon={<ArrowLeftOutlined />} className="asset-portal-detail-back" onClick={() => navigate('/assets')}>返回资产门户</Button>
            <div className="asset-portal-detail-identity">
              <span className={`asset-portal-detail-type-icon asset-portal-asset-type-${asset.assetType.toLocaleLowerCase()}`}>{assetTypeIcons[asset.assetType]}</span>
              <div className="asset-portal-detail-title">
                <div className="asset-portal-detail-tags">
                  <Tag>{assetTypeLabels[asset.assetType]}</Tag>
                  {asset.featured ? <Tag color="purple">推荐资产</Tag> : null}
                  <span className={sensitivityClass(asset.sensitivityLevel)}><SafetyCertificateOutlined /> {assetSensitivityLevelLabels[asset.sensitivityLevel]}</span>
                </div>
                <Typography.Title level={1}>{asset.name}</Typography.Title>
                <div className="asset-portal-detail-code">{asset.code || '无来源编码'}</div>
                <Typography.Paragraph>{asset.summary || '该资产暂未填写简介。'}</Typography.Paragraph>
              </div>
              <Button type="primary" size="large" icon={<LoginOutlined />} loading={sourceNavigationMutation.isPending} onClick={() => void enterSourceAsset()}>使用资产</Button>
            </div>
          </div>
        </section>

        <section className="asset-portal-container asset-portal-detail-content">
          {asset.sensitivityLevel !== 'PUBLIC' ? (
            <Alert
              showIcon
              type={asset.sensitivityLevel === 'SENSITIVE' ? 'warning' : 'info'}
              message={`${assetSensitivityLevelLabels[asset.sensitivityLevel]}资产`}
              description={asset.sensitivityLevel === 'SENSITIVE' ? '该资产含敏感业务属性。门户仅展示经过裁剪的安全元数据，实际使用仍需登录并通过来源权限校验。' : '该资产标记为内部使用。门户公开安全元数据，进入来源资源仍需相应权限。'}
            />
          ) : null}

          <section className="asset-portal-detail-panel">
            <div className="asset-portal-detail-panel-heading"><div><span className="asset-portal-section-kicker">GOVERNANCE</span><Typography.Title level={2}>资产治理信息</Typography.Title></div></div>
            <Descriptions column={{ xs: 1, sm: 2, lg: 3 }} bordered size="middle">
              <Descriptions.Item label="业务领域">{asset.directoryPath || '—'}</Descriptions.Item>
              <Descriptions.Item label="负责人">{asset.ownerName || '—'}</Descriptions.Item>
              <Descriptions.Item label="更新频率">{asset.updateFrequency || '—'}</Descriptions.Item>
              <Descriptions.Item label="敏感等级"><span className={sensitivityClass(asset.sensitivityLevel)}>{assetSensitivityLevelLabels[asset.sensitivityLevel]}</span></Descriptions.Item>
              <Descriptions.Item label="同步状态">{assetSyncStatusLabels[asset.syncStatus]}</Descriptions.Item>
              <Descriptions.Item label="发布时间">{formatDateTime(asset.publishedAt)}</Descriptions.Item>
              <Descriptions.Item label="资产标签" span={3}>{asset.tags.length > 0 ? asset.tags.map((tag) => <Tag key={tag}>{tag}</Tag>) : '—'}</Descriptions.Item>
            </Descriptions>
          </section>

          <section className="asset-portal-detail-panel">
            <div className="asset-portal-detail-panel-heading asset-portal-source-heading">
              <div><span className="asset-portal-section-kicker">SOURCE METADATA</span><Typography.Title level={2}>来源安全元数据</Typography.Title><Typography.Paragraph>{sourceStatusDescription(asset.sourceDisplayStatus)}</Typography.Paragraph></div>
              <Tag color={sourceStatusTone[asset.sourceDisplayStatus]}>{assetPortalSourceDisplayStatusLabels[asset.sourceDisplayStatus]}</Tag>
            </div>
            {asset.sourceDisplayStatus !== 'AVAILABLE' ? <Alert className="asset-portal-source-alert" type={asset.sourceDisplayStatus === 'UNAVAILABLE' ? 'warning' : 'info'} showIcon message={sourceStatusDescription(asset.sourceDisplayStatus)} /> : null}
            <Descriptions className="asset-portal-source-basics" column={{ xs: 1, sm: 2, lg: 3 }} bordered size="middle">
              <Descriptions.Item label="来源名称">{asset.sourceName || '—'}</Descriptions.Item>
              <Descriptions.Item label="来源编码">{asset.sourceCode || '—'}</Descriptions.Item>
              <Descriptions.Item label="来源状态">{asset.sourceStatus || '—'}</Descriptions.Item>
              <Descriptions.Item label="来源更新时间">{formatDateTime(asset.sourceUpdatedAt)}</Descriptions.Item>
              <Descriptions.Item label="来源说明" span={2}>{asset.sourceDescription || '—'}</Descriptions.Item>
            </Descriptions>
            <div className="asset-portal-metadata-title">{assetTypeLabels[asset.assetType]}元数据</div>
            <MetadataPanel asset={asset} />
          </section>
        </section>
      </main>

      <footer className="asset-portal-footer">
        <div className="asset-portal-container"><span>DataScalpel 数据资产门户</span><span>公开安全元数据 · 来源权限内使用</span></div>
      </footer>
    </div>
  );
};
