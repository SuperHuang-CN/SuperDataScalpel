import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataMaskingRuleRequest,
  DataMaskingRule,
  UpdateDataMaskingRuleRequest,
} from '../model/maskingRule';

const MASKING_RULE_PATH = '/v1/masking-rules';

export const fetchMaskingRules = async (
  request: SearchRequest,
): Promise<PageResponse<DataMaskingRule>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataMaskingRule>>(
    query ? `${MASKING_RULE_PATH}?${query}` : MASKING_RULE_PATH,
  );
};

export const fetchMaskingRule = (id: string): Promise<DataMaskingRule> => (
  requestJson<DataMaskingRule>(`${MASKING_RULE_PATH}/${id}`)
);

export const createMaskingRule = (
  request: CreateDataMaskingRuleRequest,
): Promise<DataMaskingRule> => requestJson<DataMaskingRule>(MASKING_RULE_PATH, {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateMaskingRule = (
  id: string,
  request: UpdateDataMaskingRuleRequest,
): Promise<DataMaskingRule> => requestJson<DataMaskingRule>(
  `${MASKING_RULE_PATH}/${id}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const deleteMaskingRule = (id: string): Promise<void> => requestJson<void>(
  `${MASKING_RULE_PATH}/${id}/actions/delete`,
  { method: 'POST' },
);
