export type DataServiceDetailTabKey = 'basic' | 'definition' | 'cartography' | 'models' | 'lineage' | 'runtime';

export const normalizeDataServiceDetailTab = (value: string | null): DataServiceDetailTabKey => {
  if (value === 'definition' || value === 'cartography' || value === 'models' || value === 'lineage' || value === 'runtime') return value;
  return 'basic';
};
