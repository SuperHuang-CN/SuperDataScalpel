export type ServiceEngineType = 'DATASCALPEL' | 'GEOSERVER';

export interface ServiceEngine {
  id: string;
  type?: ServiceEngineType;
  code: string;
  name: string;
  adminUrl: string;
  runtimeUrl: string;
  managementTokenConfigured: boolean;
  geoServerUsername?: string | null;
  geoServerWorkspace?: string | null;
  geoServerCredentialConfigured?: boolean;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceEngineTestResult {
  type?: ServiceEngineType;
  code: string;
  version?: string | null;
  databaseTypes: string[];
  capabilities?: string[];
  normalizedAdminUrl?: string | null;
  normalizedRuntimeUrl?: string | null;
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
  type?: ServiceEngineType;
  code?: string;
  name: string;
  adminUrl: string;
  runtimeUrl: string;
  managementToken?: string;
  geoServerUsername?: string;
  geoServerPassword?: string;
  geoServerWorkspace?: string;
  enabled?: boolean;
  description?: string;
}

export type CreateServiceEngineRequest = ServiceEngineWriteRequest & {
  type: ServiceEngineType;
};

export type UpdateServiceEngineRequest = ServiceEngineWriteRequest & { enabled: boolean };

export interface TestServiceEngineRequest {
  type: ServiceEngineType;
  code?: string;
  adminUrl: string;
  runtimeUrl: string;
  managementToken?: string;
  geoServerUsername?: string;
  geoServerPassword?: string;
  geoServerWorkspace?: string;
}

export interface TestStoredServiceEngineRequest {
  adminUrl?: string;
  runtimeUrl?: string;
  managementToken?: string;
  geoServerUsername?: string;
  geoServerPassword?: string;
  geoServerWorkspace?: string;
}

export interface ServiceEngineFilters {
  keyword?: string;
  enabled?: boolean;
  type?: ServiceEngineType;
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
