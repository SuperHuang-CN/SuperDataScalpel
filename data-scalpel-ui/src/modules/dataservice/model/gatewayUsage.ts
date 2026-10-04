export interface GatewayUsage {
 collectedAt: string; from: string; to: string; collectionEnabled: boolean; hasSamples: boolean;
 activeServices: number; activeConsumers: number; successCount: number; serverErrorCount: number;
 latestRecordedHour: string | null;
}
