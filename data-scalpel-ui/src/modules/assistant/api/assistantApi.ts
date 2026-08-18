import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  AssistantChangeSet,
  AssistantMessage,
  AssistantSession,
  AssistantSessionDetail,
  AssistantTurn,
  AvailableLlmModel,
  LlmModelConfiguration,
  SaveLlmModelRequest,
  SendAssistantMessageRequest,
} from '../model/assistant';

const MODEL_PATH = '/v1/system/llm-models';
const ASSISTANT_PATH = '/v1/assistant';

const searchPath = (path: string, request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchLlmModels = (request: SearchRequest) => (
  requestJson<PageResponse<LlmModelConfiguration>>(searchPath(MODEL_PATH, request))
);

export const createLlmModel = (request: SaveLlmModelRequest) => requestJson<LlmModelConfiguration>(MODEL_PATH, {
  method: 'POST', body: JSON.stringify(request),
});

export const updateLlmModel = (id: string, request: SaveLlmModelRequest) => (
  requestJson<LlmModelConfiguration>(`${MODEL_PATH}/${id}/actions/update`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const commandLlmModel = (
  id: string,
  command: 'test' | 'enable' | 'disable' | 'set-default',
) => requestJson<LlmModelConfiguration>(
  `${MODEL_PATH}/${id}/actions/${command}`,
  { method: 'POST' },
  command === 'test' ? 100_000 : undefined,
);

export const deleteLlmModel = (id: string) => requestJson<void>(`${MODEL_PATH}/${id}/actions/delete`, { method: 'POST' });

export const fetchAvailableAssistantModels = () => requestJson<AvailableLlmModel[]>(`${ASSISTANT_PATH}/models`);

export const fetchAssistantSessions = (request: SearchRequest) => (
  requestJson<PageResponse<AssistantSession>>(searchPath(`${ASSISTANT_PATH}/sessions`, request))
);

export const createAssistantSession = (modelId?: string) => requestJson<AssistantSession>(`${ASSISTANT_PATH}/sessions`, {
  method: 'POST', body: JSON.stringify({ modelId }),
});

export const fetchAssistantSession = (id: string) => (
  requestJson<AssistantSessionDetail>(`${ASSISTANT_PATH}/sessions/${id}`)
);

export const fetchAssistantMessages = (id: string) => (
  requestJson<PageResponse<AssistantMessage>>(`${ASSISTANT_PATH}/sessions/${id}/messages?page=0&size=100`)
);

export const selectAssistantModel = (id: string, modelId: string) => requestJson<AssistantSession>(
  `${ASSISTANT_PATH}/sessions/${id}/actions/select-model`,
  { method: 'POST', body: JSON.stringify({ modelId }) },
);

export const archiveAssistantSession = (id: string) => requestJson<AssistantSession>(
  `${ASSISTANT_PATH}/sessions/${id}/actions/archive`, { method: 'POST' },
);

export const sendAssistantMessage = (id: string, request: SendAssistantMessageRequest) => requestJson<AssistantTurn>(
  `${ASSISTANT_PATH}/sessions/${id}/actions/message`,
  { method: 'POST', body: JSON.stringify(request) },
  100_000,
);

export const approveAssistantChangeSet = (id: string) => requestJson<AssistantChangeSet>(
  `${ASSISTANT_PATH}/change-sets/${id}/actions/approve`, { method: 'POST' },
);

export const rejectAssistantChangeSet = (id: string) => requestJson<AssistantChangeSet>(
  `${ASSISTANT_PATH}/change-sets/${id}/actions/reject`, { method: 'POST' },
);
