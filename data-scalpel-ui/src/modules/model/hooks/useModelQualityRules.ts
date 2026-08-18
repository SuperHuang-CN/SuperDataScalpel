import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acceptModelQualityRuleSuggestions,
  createModelQualityRule,
  deleteModelQualityRule,
  executeModelQualityRuleCommand,
  fetchModelQualityRuleSuggestions,
  fetchModelQualityOverview,
  fetchModelQualityRules,
  updateModelQualityRule,
  type ModelQualityRuleCommand,
} from '../api/modelQualityRuleApi';
import type {
  CreateModelQualityRuleRequest,
  UpdateModelQualityRuleRequest,
} from '../model/modelQualityRule';

export const modelQualityRulesQueryKey = 'model-quality-rules';
export const modelQualityOverviewQueryKey = 'model-quality-overview';

export const useModelQualityRules = (modelId: string, enabled = true) => useQuery({
  queryKey: [modelQualityRulesQueryKey, modelId],
  queryFn: () => fetchModelQualityRules(modelId),
  enabled: enabled && Boolean(modelId),
});

export const useModelQualityOverview = (modelId: string, enabled = true) => useQuery({
  queryKey: [modelQualityOverviewQueryKey, modelId],
  queryFn: () => fetchModelQualityOverview(modelId),
  enabled: enabled && Boolean(modelId),
  refetchInterval: (query) => {
    const status = query.state.data?.latestRun?.status;
    return status === 'QUEUED' || status === 'RUNNING' || status === 'CANCEL_REQUESTED'
      || status === 'STOP_REQUESTED' ? 5_000 : false;
  },
});

export const useModelQualityRuleSuggestions = (modelId: string, enabled = true) => useQuery({
  queryKey: [modelQualityRulesQueryKey, modelId, 'suggestions'],
  queryFn: () => fetchModelQualityRuleSuggestions(modelId),
  enabled: enabled && Boolean(modelId),
});

const invalidate = (queryClient: ReturnType<typeof useQueryClient>, modelId: string) => Promise.all([
  queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey, modelId] }),
  queryClient.invalidateQueries({ queryKey: [modelQualityOverviewQueryKey, modelId] }),
]);

export const useCreateModelQualityRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, request }: { modelId: string; request: CreateModelQualityRuleRequest }) => (
      createModelQualityRule(modelId, request)
    ),
    onSuccess: (rule) => invalidate(queryClient, rule.modelId),
  });
};

export const useUpdateModelQualityRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateModelQualityRuleRequest }) => (
      updateModelQualityRule(id, request)
    ),
    onSuccess: (rule) => invalidate(queryClient, rule.modelId),
  });
};

export const useModelQualityRuleCommand = (command: ModelQualityRuleCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeModelQualityRuleCommand(id, command),
    onSuccess: (rule) => invalidate(queryClient, rule.modelId),
  });
};

export const useDeleteModelQualityRule = (modelId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteModelQualityRule,
    onSuccess: () => invalidate(queryClient, modelId),
  });
};

export const useAcceptModelQualityRuleSuggestions = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, suggestionKeys }: {
      modelId: string;
      suggestionKeys: string[];
    }) => acceptModelQualityRuleSuggestions(modelId, suggestionKeys),
    onSuccess: (_rules, variables) => invalidate(queryClient, variables.modelId),
  });
};
