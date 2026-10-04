import { useQuery } from '@tanstack/react-query';
import { fetchQualityStatistics, fetchQualityModels } from '../api/qualityStatisticsApi';
import type { QualityResultFilter } from '../model/qualityStatistics';
export const useQualityStatistics = (enabled: boolean) => useQuery({
 queryKey: ['model', 'quality-statistics'], queryFn: fetchQualityStatistics, enabled,
 staleTime: 30_000, refetchInterval: 60_000, refetchIntervalInBackground: false,
});
export const useQualityModels = (result: QualityResultFilter, page: number, size: number, enabled: boolean) => useQuery({
 queryKey: ['model', 'quality-statistics', result, page, size],
 queryFn: () => fetchQualityModels(result, page, size), enabled,
});
