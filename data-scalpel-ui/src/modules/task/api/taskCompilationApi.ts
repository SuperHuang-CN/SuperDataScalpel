import { requestJson } from '../../../shared/api/http';
import type {
  TaskCompilationCancellationResponse,
  TaskCompilationRequest,
  TaskCompilationResponse,
} from '../canvas/taskCompilationTypes';

const TASK_COMPILATION_PATH = '/v1/task-compilations';

export const compileCanvasTask = (
  request: TaskCompilationRequest,
  signal?: AbortSignal,
): Promise<TaskCompilationResponse> => requestJson<TaskCompilationResponse>(TASK_COMPILATION_PATH, {
  method: 'POST',
  body: JSON.stringify(request),
  signal,
}, 40_000);

export const cancelTaskCompilation = (
  requestId: string,
): Promise<TaskCompilationCancellationResponse> => requestJson<TaskCompilationCancellationResponse>(
  `${TASK_COMPILATION_PATH}/${requestId}/actions/cancel`,
  { method: 'POST' },
  10_000,
);
