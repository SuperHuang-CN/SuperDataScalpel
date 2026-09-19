import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type { SystemConfiguration, UpdateSystemConfigurationRequest } from '../model/systemConfiguration';

const SYSTEM_CONFIGURATION_PATH = '/v1/system/configurations';

export const fetchSystemConfigurations = async (
  request: SearchRequest,
): Promise<PageResponse<SystemConfiguration>> => {
  const query = toSearchParams(request).toString();
  const path = query ? `${SYSTEM_CONFIGURATION_PATH}?${query}` : SYSTEM_CONFIGURATION_PATH;
  return requestJson<PageResponse<SystemConfiguration>>(path);
};

export const updateSystemConfiguration = (
  id: string,
  request: UpdateSystemConfigurationRequest,
): Promise<SystemConfiguration> => requestJson<SystemConfiguration>(`${SYSTEM_CONFIGURATION_PATH}/${id}/actions/update`, {
  method: 'POST',
  body: JSON.stringify(request),
});
