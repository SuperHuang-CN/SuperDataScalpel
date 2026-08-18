import type { DataServiceType } from '../model/dataService';

export type DataServiceTypeIconTone = 'blue' | 'cyan' | 'violet';

export const dataServiceTypeIconTones = {
  STANDARD_TABLE: 'blue',
  SQL_QUERY: 'cyan',
  SCRIPT_API: 'violet',
} satisfies Record<DataServiceType, DataServiceTypeIconTone>;
