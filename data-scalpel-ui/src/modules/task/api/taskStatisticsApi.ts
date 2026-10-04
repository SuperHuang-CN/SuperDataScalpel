import { requestJson } from '../../../shared/api/http';
import type { TaskStatistics } from '../model/taskStatistics';
export const fetchTaskStatistics = () => requestJson<TaskStatistics>('/v1/tasks/statistics');
