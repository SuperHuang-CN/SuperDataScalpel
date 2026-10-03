export type QualityResultFilter = 'ALL' | 'PASSED' | 'FAILED' | 'NONE' | 'EXECUTION_FAILED';
export interface QualityStatistics {
 collectedAt: string; total: number; passed: number; failed: number; noResult: number; latestExecutionFailed: number;
 oldestResultAt: string | null; newestResultAt: string | null;
}
export interface QualityStatisticsItem {
 modelId: string; modelName: string; runId: string | null; conclusion: 'PASSED' | 'FAILED' | null;
 endedAt: string | null; ruleSnapshotAt: string | null; latestExecutionFailed: boolean;
}
