export interface ServiceEngine {
  id: string;
  code: string;
  name: string;
  adminUrl: string;
  runtimeUrl: string;
  managementTokenConfigured: boolean;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceEngineTestResult {
  code: string;
  databaseTypes: string[];
  elapsedMs: number;
}

export type ServiceEngineDataSourceRegistrationStatus = 'PENDING' | 'READY' | 'OUTDATED' | 'FAILED';

export interface ServiceEngineDataSourceRegistration {
  id: string;
  engineId: string;
  engineCode: string;
  engineName: string;
  dataSourceId: string;
  dataSourceCode: string;
  dataSourceName: string;
  databaseType: string | null;
  status: ServiceEngineDataSourceRegistrationStatus;
  synchronizedAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceEngineDataSourceTestResult {
  registrationId: string;
  engineCode: string;
  dataSourceId: string;
  databaseType: string;
}

interface ServiceEngineWriteRequest {
  name: string;
  adminUrl: string;
  runtimeUrl: string;
  managementToken?: string;
  enabled?: boolean;
  description?: string;
}

export type CreateServiceEngineRequest = ServiceEngineWriteRequest & {
  managementToken: string;
};

export type UpdateServiceEngineRequest = ServiceEngineWriteRequest & { enabled: boolean };

export interface TestServiceEngineRequest {
  adminUrl: string;
  managementToken: string;
}

export interface TestStoredServiceEngineRequest {
  adminUrl?: string;
  managementToken?: string;
}

export interface ServiceEngineFilters {
  keyword?: string;
  enabled?: boolean;
}

export type ServiceEngineAccessPolicyStatus = 'NOT_CONFIGURED' | 'PENDING' | 'READY' | 'FAILED' | 'OUTDATED';

export interface ServiceEngineAccessPolicy {
  engineId: string;
  allowCidrs: string[];
  denyCidrs: string[];
  desiredRevision: number;
  appliedRevision: number;
  status: ServiceEngineAccessPolicyStatus;
  lastError: string | null;
  appliedAt: string | null;
  updatedAt: string | null;
}

export interface UpdateServiceEngineAccessPolicyRequest {
  allowCidrs: string[];
  denyCidrs: string[];
}
