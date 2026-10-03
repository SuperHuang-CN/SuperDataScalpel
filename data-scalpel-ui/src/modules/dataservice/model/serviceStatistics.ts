import type { DataServiceType } from './dataService';
export interface ServiceStatistics {
 collectedAt: string; enabled: number; failed: number; unconfirmed: number; gatewayPublished: number;
 types: { type: DataServiceType; enabled: number; failed: number; unconfirmed: number }[];
}
