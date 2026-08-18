import { requestJson } from '../../../shared/api/http';
import type {
  CreateModelQualityRuleRequest,
  ModelQualityRule,
  ModelQualityRuleSuggestion,
  UpdateModelQualityRuleRequest,
} from '../model/modelQualityRule';
import type { ModelQualityOverview } from '../model/modelQualityOverview';

const modelRulePath = (modelId: string) => `/v1/models/${modelId}/quality-rules`;
const rulePath = (id: string) => `/v1/model-quality-rules/${id}`;

export const fetchModelQualityRules = (modelId: string): Promise<ModelQualityRule[]> => (
  requestJson<ModelQualityRule[]>(modelRulePath(modelId))
);

export const fetchModelQualityOverview = (modelId: string): Promise<ModelQualityOverview> => (
  requestJson<ModelQualityOverview>(`/v1/models/${modelId}/quality-overview`)
);

export const fetchModelQualityRuleSuggestions = (modelId: string): Promise<ModelQualityRuleSuggestion[]> => (
  requestJson<ModelQualityRuleSuggestion[]>(`/v1/models/${modelId}/quality-rule-suggestions`)
);

export const createModelQualityRule = (
  modelId: string,
  request: CreateModelQualityRuleRequest,
): Promise<ModelQualityRule> => requestJson<ModelQualityRule>(modelRulePath(modelId), {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateModelQualityRule = (
  id: string,
  request: UpdateModelQualityRuleRequest,
): Promise<ModelQualityRule> => requestJson<ModelQualityRule>(`${rulePath(id)}/actions/update`, {
  method: 'POST',
  body: JSON.stringify(request),
});

export type ModelQualityRuleCommand = 'enable' | 'disable';

export const executeModelQualityRuleCommand = (
  id: string,
  command: ModelQualityRuleCommand,
): Promise<ModelQualityRule> => requestJson<ModelQualityRule>(`${rulePath(id)}/actions/${command}`, {
  method: 'POST',
});

export const deleteModelQualityRule = (id: string): Promise<void> => requestJson<void>(
  `${rulePath(id)}/actions/delete`,
  { method: 'POST' },
);

export const acceptModelQualityRuleSuggestions = (
  modelId: string,
  suggestionKeys: string[],
): Promise<ModelQualityRule[]> => requestJson<ModelQualityRule[]>(
  `${modelRulePath(modelId)}/actions/accept-suggestions`,
  { method: 'POST', body: JSON.stringify({ suggestionKeys }) },
);
