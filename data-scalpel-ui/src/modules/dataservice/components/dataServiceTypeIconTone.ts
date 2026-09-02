import type { DataServiceType } from '../model/dataService';

export type DataServiceTypeIconTone = 'blue' | 'cyan' | 'violet';

export const dataServiceTypeIconTones = {
  STANDARD_TABLE: 'blue',
  SQL_QUERY: 'cyan',
  SCRIPT_API: 'violet',
  SPATIAL_SERVICE: 'cyan',
} satisfies Record<DataServiceType, DataServiceTypeIconTone>;
