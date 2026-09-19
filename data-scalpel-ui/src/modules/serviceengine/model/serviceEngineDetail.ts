export const serviceEngineDetailTabs = ['basic', 'services', 'datasources', 'access-policy'] as const;

export type ServiceEngineDetailTabKey = (typeof serviceEngineDetailTabs)[number];

export const normalizeServiceEngineDetailTab = (value: string | null): ServiceEngineDetailTabKey => (
  serviceEngineDetailTabs.includes(value as ServiceEngineDetailTabKey)
    ? value as ServiceEngineDetailTabKey
    : 'basic'
);
