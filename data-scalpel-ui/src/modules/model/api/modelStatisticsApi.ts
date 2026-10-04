import { requestJson } from '../../../shared/api/http';
import type { ModelStatistics } from '../model/modelStatistics';
export const fetchModelStatistics = () => requestJson<ModelStatistics>('/v1/models/statistics');
