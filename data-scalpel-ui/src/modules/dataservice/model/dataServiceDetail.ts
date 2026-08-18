export type DataServiceDetailTabKey = 'basic' | 'definition' | 'models' | 'lineage' | 'runtime';

export const normalizeDataServiceDetailTab = (value: string | null): DataServiceDetailTabKey => {
  if (value === 'definition' || value === 'models' || value === 'lineage' || value === 'runtime') return value;
  return 'basic';
};
