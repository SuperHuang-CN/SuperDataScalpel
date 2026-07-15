export interface ServiceEngine {
  id: string;
  code: string;
  name: string;
  adminUrl: string;
  publicUrl: string;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceEngineTestResult {
  code: string;
  databaseTypes: string[];
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
  revision: number;
  synchronizedAt: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServiceEngineDataSourceTestResult {
  registrationId: string;
  engineCode: string;
  dataSourceId: string;
  revision: number;
  databaseType: string;
}

export interface CreateServiceEngineRequest {
  code: string;
  name: string;
  adminUrl: string;
  publicUrl: string;
  enabled?: boolean;
  description?: string;
}

export type UpdateServiceEngineRequest = Omit<CreateServiceEngineRequest, 'code'> & { enabled: boolean };

export interface ServiceEngineFilters {
  keyword?: string;
  enabled?: boolean;
}
