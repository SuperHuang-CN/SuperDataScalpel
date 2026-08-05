export type DataServiceDetailTabKey = 'basic' | 'models' | 'lineage' | 'runtime';

export const normalizeDataServiceDetailTab = (value: string | null): DataServiceDetailTabKey => {
  if (value === 'models' || value === 'lineage' || value === 'runtime') return value;
  return 'basic';
};
