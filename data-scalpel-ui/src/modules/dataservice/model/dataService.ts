export type DataServiceStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export type DataServiceDeploymentStatus = 'PENDING' | 'DEPLOYED' | 'FAILED' | 'REMOVING' | 'REMOVED';

export interface DataService {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  modelId: string;
  engineId: string;
  routePath: string;
  status: DataServiceStatus;
  revision: number;
  deploymentStatus: DataServiceDeploymentStatus | null;
  deploymentError: string | null;
  deployedAt: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDataServiceRequest {
  code: string;
  name: string;
  directoryId?: string;
  modelId: string;
  engineId: string;
  routePath: string;
  description?: string;
}

export type UpdateDataServiceRequest = Omit<CreateDataServiceRequest, 'code'>;

export interface DataServiceFilters {
  keyword?: string;
  status?: DataServiceStatus;
  engineId?: string;
  modelId?: string;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const dataServiceStatusLabels: Record<DataServiceStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
};

export const dataServiceDeploymentStatusLabels: Record<DataServiceDeploymentStatus, string> = {
  PENDING: '待部署',
  DEPLOYED: '已部署',
  FAILED: '失败',
  REMOVING: '下线中',
  REMOVED: '已移除',
};
