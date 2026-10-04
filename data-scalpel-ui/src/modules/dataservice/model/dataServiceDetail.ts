export type DataServiceDetailTabKey = 'basic' | 'definition' | 'cartography' | 'models' | 'lineage' | 'runtime' | 'gateway-policy';

export const normalizeDataServiceDetailTab = (value: string | null): DataServiceDetailTabKey => {
  if (value === 'definition' || value === 'cartography' || value === 'models' || value === 'lineage' || value === 'runtime' || value === 'gateway-policy') return value;
  return 'basic';
};
