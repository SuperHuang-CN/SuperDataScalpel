export type SystemConfigurationValueType = 'STRING' | 'INTEGER' | 'BOOLEAN';

export interface SystemConfiguration {
  id: string;
  configKey: string;
  name: string;
  configValue: string;
  valueType: SystemConfigurationValueType;
  description: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateSystemConfigurationRequest {
  configValue: string;
}

export interface SystemConfigurationFilters {
  name?: string;
  configKey?: string;
}
