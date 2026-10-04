import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import type { QualityResultFilter, QualityStatistics, QualityStatisticsItem } from '../model/qualityStatistics';
export const fetchQualityStatistics = () => requestJson<QualityStatistics>('/v1/model-quality/statistics');
export const fetchQualityModels = (result: QualityResultFilter, page: number, size: number) =>
 requestJson<PageResponse<QualityStatisticsItem>>('/v1/model-quality/models?' + new URLSearchParams({ result, page: String(page), size: String(size) }));
