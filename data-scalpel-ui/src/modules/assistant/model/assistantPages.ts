export interface AssistantPageDefinition {
  key: string;
  path: string;
  permission?: string;
}

const pages: AssistantPageDefinition[] = [
  { key: 'DASHBOARD', path: '/' },
  { key: 'ASSET_PORTAL', path: '/assets' },
  { key: 'ASSET_MANAGEMENT', path: '/asset-management/assets', permission: 'asset.view' },
  { key: 'ASSET_DOMAINS', path: '/asset-management/domains', permission: 'directory.view' },
  { key: 'DATA_SOURCE_LIST', path: '/datasource', permission: 'datasource.view' },
  { key: 'FILE_DATASET_LIST', path: '/file-dataset', permission: 'filedataset.view' },
  { key: 'DICTIONARY_LIST', path: '/standard/dictionaries', permission: 'standard.dictionary.view' },
  { key: 'MODEL_LIST', path: '/model', permission: 'model.view' },
  { key: 'MODEL_FIELD_TEMPLATES', path: '/model/field-templates', permission: 'model.view' },
  { key: 'DATA_ENTRY_LIST', path: '/data-entry', permission: 'dataentry.view' },
  { key: 'TASK_LIST', path: '/task', permission: 'task.view' },
  { key: 'TASK_ORCHESTRATION', path: '/task/orchestration', permission: 'task.view' },
  { key: 'TASK_MASKING_RULES', path: '/task/masking-rules', permission: 'task.view' },
  { key: 'DATA_SERVICE_LIST', path: '/dataservice', permission: 'service.view' },
  { key: 'DATA_SERVICE_CONSUMERS', path: '/dataservice/consumers', permission: 'service.view' },
  { key: 'DATA_SERVICE_OPERATIONS', path: '/dataservice/operations', permission: 'service.view' },
  { key: 'SERVICE_ENGINE', path: '/service-engine', permission: 'service.engine.view' },
  { key: 'COMPUTE_ENGINE', path: '/compute-engine', permission: 'compute.engine.view' },
  { key: 'SYSTEM_CONFIGURATIONS', path: '/system/configurations', permission: 'system.configuration.view' },
  { key: 'SYSTEM_WAREHOUSE_LAYERS', path: '/system/model-warehouse-layers', permission: 'system.configuration.view' },
  { key: 'SYSTEM_AI_MODELS', path: '/system/ai-models', permission: 'system.configuration.view' },
  { key: 'SYSTEM_USERS', path: '/system/users', permission: 'system.user.view' },
  { key: 'SYSTEM_ROLES', path: '/system/roles', permission: 'system.role.view' },
  { key: 'SYSTEM_PERMISSIONS', path: '/system/permissions', permission: 'system.permission.view' },
];

const pageByKey = new Map(pages.map((page) => [page.key, page]));

const pathPrefixes: Array<[RegExp, string]> = [
  [/^\/asset-management\/assets(?:\/|$)/, 'ASSET_MANAGEMENT'],
  [/^\/asset-management\/domains(?:\/|$)/, 'ASSET_DOMAINS'],
  [/^\/system\/model-warehouse-layers(?:\/|$)/, 'SYSTEM_WAREHOUSE_LAYERS'],
  [/^\/system\/configurations(?:\/|$)/, 'SYSTEM_CONFIGURATIONS'],
  [/^\/system\/ai-models(?:\/|$)/, 'SYSTEM_AI_MODELS'],
  [/^\/system\/permissions(?:\/|$)/, 'SYSTEM_PERMISSIONS'],
  [/^\/system\/users(?:\/|$)/, 'SYSTEM_USERS'],
  [/^\/system\/roles(?:\/|$)/, 'SYSTEM_ROLES'],
  [/^\/model\/field-templates(?:\/|$)/, 'MODEL_FIELD_TEMPLATES'],
  [/^\/task\/orchestration(?:\/|$)/, 'TASK_ORCHESTRATION'],
  [/^\/task\/masking-rules(?:\/|$)/, 'TASK_MASKING_RULES'],
  [/^\/dataservice\/consumers(?:\/|$)/, 'DATA_SERVICE_CONSUMERS'],
  [/^\/dataservice\/operations(?:\/|$)/, 'DATA_SERVICE_OPERATIONS'],
  [/^\/datasource(?:\/|$)/, 'DATA_SOURCE_LIST'],
  [/^\/file-dataset(?:\/|$)/, 'FILE_DATASET_LIST'],
  [/^\/standard\/dictionaries(?:\/|$)/, 'DICTIONARY_LIST'],
  [/^\/model(?:\/|$)/, 'MODEL_LIST'],
  [/^\/data-entry(?:\/|$)/, 'DATA_ENTRY_LIST'],
  [/^\/task(?:\/|$)/, 'TASK_LIST'],
  [/^\/dataservice(?:\/|$)/, 'DATA_SERVICE_LIST'],
  [/^\/service-engine(?:\/|$)/, 'SERVICE_ENGINE'],
  [/^\/compute-engine(?:\/|$)/, 'COMPUTE_ENGINE'],
];

export const assistantPageKeyForPath = (pathname: string): string => (
  pathname === '/' ? 'DASHBOARD' : pathPrefixes.find(([pattern]) => pattern.test(pathname))?.[1] ?? 'DASHBOARD'
);

export const assistantPagePath = (pageKey: string, permissions: Set<string>): string | null => {
  const page = pageByKey.get(pageKey);
  if (!page || (page.permission && !permissions.has(page.permission))) return null;
  return page.path;
};
