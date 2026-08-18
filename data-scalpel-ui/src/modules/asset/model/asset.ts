export type AssetType = 'DATA_MODEL' | 'FILE_DATASET' | 'DICTIONARY' | 'DATA_SERVICE';
export type AssetStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
export type AssetSyncStatus = 'IN_SYNC' | 'OUTDATED' | 'SOURCE_UNAVAILABLE' | 'SOURCE_MISSING' | 'FAILED';
export type AssetSensitivityLevel = 'PUBLIC' | 'INTERNAL' | 'SENSITIVE';
export type AssetPortalSourceDisplayStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'CACHED';

export interface Asset {
  id: string;
  assetType: AssetType;
  resourceId: string;
  directoryId: string | null;
  status: AssetStatus;
  effectiveName: string;
  effectiveSummary: string | null;
  portalName: string | null;
  portalSummary: string | null;
  tags: string[];
  ownerName: string | null;
  updateFrequency: string | null;
  sensitivityLevel: AssetSensitivityLevel | null;
  featured: boolean;
  publishedAt: string | null;
  offlineAt: string | null;
  sourceName: string;
  sourceCode: string | null;
  sourceDescription: string | null;
  sourceStatus: string;
  sourceUpdatedAt: string | null;
  sourceSnapshot: Record<string, unknown>;
  lastCheckedAt: string;
  lastSyncedAt: string;
  syncStatus: AssetSyncStatus;
  syncError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AssetCandidate {
  assetType: AssetType;
  resourceId: string;
  name: string;
  code: string | null;
  description: string | null;
  sourceStatus: string;
  sourceUpdatedAt: string | null;
  eligible: boolean;
  ineligibleReason: string | null;
  registered: boolean;
  assetId: string | null;
}

export interface UpdateAssetRequest {
  directoryId?: string;
  portalName?: string;
  portalSummary?: string;
  tags: string[];
  ownerName?: string;
  updateFrequency?: string;
  sensitivityLevel?: AssetSensitivityLevel;
  featured: boolean;
}

export interface AssetBatchOperationResult {
  totalCount: number;
  successCount: number;
  outdatedCount: number;
  unavailableCount: number;
  missingCount: number;
  failedCount: number;
  failures: Array<{ assetId: string; assetName: string; message: string }>;
}

export interface AssetPortalDomain {
  id: string;
  name: string;
  description: string | null;
  assetCount: number;
}

export interface AssetPortalOverview {
  totalCount: number;
  typeCounts: Record<AssetType, number>;
  popularTags: string[];
  domains: AssetPortalDomain[];
}

export interface AssetPortalAssetSummary {
  id: string;
  assetType: AssetType;
  name: string;
  code: string | null;
  summary: string | null;
  directoryPath: string | null;
  tags: string[];
  ownerName: string | null;
  updateFrequency: string | null;
  sensitivityLevel: AssetSensitivityLevel;
  syncStatus: AssetSyncStatus;
  featured: boolean;
  publishedAt: string;
  sourceUpdatedAt: string | null;
}

export interface AssetPortalAssetDetail extends AssetPortalAssetSummary {
  sourceDisplayStatus: AssetPortalSourceDisplayStatus;
  sourceName: string;
  sourceCode: string | null;
  sourceDescription: string | null;
  sourceStatus: string;
  metadata: Record<string, unknown>;
}

export interface AssetPortalFilters {
  keyword?: string;
  assetType?: AssetType;
  directoryId?: string;
}

export interface AssetSourceNavigation {
  path: string;
}

export const assetTypeLabels: Record<AssetType, string> = {
  DATA_MODEL: '数据模型',
  FILE_DATASET: '文件数据集',
  DICTIONARY: '码表',
  DATA_SERVICE: '数据服务',
};

export const assetStatusLabels: Record<AssetStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
};

export const assetSyncStatusLabels: Record<AssetSyncStatus, string> = {
  IN_SYNC: '已同步',
  OUTDATED: '待同步',
  SOURCE_UNAVAILABLE: '来源不可用',
  SOURCE_MISSING: '来源不存在',
  FAILED: '检查失败',
};

export const assetSensitivityLevelLabels: Record<AssetSensitivityLevel, string> = {
  PUBLIC: '公开',
  INTERNAL: '内部',
  SENSITIVE: '敏感',
};

export const assetPortalSourceDisplayStatusLabels: Record<AssetPortalSourceDisplayStatus, string> = {
  AVAILABLE: '来源可用',
  UNAVAILABLE: '来源不可用',
  CACHED: '缓存快照',
};

export const assetSourcePath = (asset: Pick<Asset, 'assetType' | 'resourceId'>): string => ({
  DATA_MODEL: `/model/${asset.resourceId}`,
  FILE_DATASET: `/file-dataset/${asset.resourceId}`,
  DICTIONARY: `/standard/dictionaries/${asset.resourceId}`,
  DATA_SERVICE: `/dataservice/${asset.resourceId}`,
})[asset.assetType];
