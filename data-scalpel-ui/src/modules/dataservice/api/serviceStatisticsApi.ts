import { requestJson } from '../../../shared/api/http';
import type { ServiceStatistics } from '../model/serviceStatistics';
export const fetchServiceStatistics = () => requestJson<ServiceStatistics>('/v1/data-services/statistics');
