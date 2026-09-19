import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import {
  createMaskingRule,
  deleteMaskingRule,
  fetchMaskingRule,
  fetchMaskingRules,
  updateMaskingRule,
} from '../api/maskingRuleApi';
import type {
  CreateDataMaskingRuleRequest,
  UpdateDataMaskingRuleRequest,
} from '../model/maskingRule';

const maskingRulesKey = 'masking-rules';

export const useMaskingRules = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [maskingRulesKey, request],
  queryFn: () => fetchMaskingRules(request),
  enabled,
});

export const useMaskingRule = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [maskingRulesKey, id],
  queryFn: () => fetchMaskingRule(id as string),
  enabled: Boolean(id) && enabled,
  retry: false,
});

export const useCreateMaskingRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDataMaskingRuleRequest) => createMaskingRule(request),
    onSuccess: async (rule) => {
      queryClient.setQueryData([maskingRulesKey, rule.id], rule);
      await queryClient.invalidateQueries({ queryKey: [maskingRulesKey] });
    },
  });
};

export const useUpdateMaskingRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      request,
    }: {
      id: string;
      request: UpdateDataMaskingRuleRequest;
    }) => updateMaskingRule(id, request),
    onSuccess: async (rule) => {
      queryClient.setQueryData([maskingRulesKey, rule.id], rule);
      await queryClient.invalidateQueries({ queryKey: [maskingRulesKey] });
    },
  });
};

export const useDeleteMaskingRule = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteMaskingRule,
    onSuccess: async (_result, id) => {
      queryClient.removeQueries({ queryKey: [maskingRulesKey, id] });
      await queryClient.invalidateQueries({ queryKey: [maskingRulesKey] });
    },
  });
};
