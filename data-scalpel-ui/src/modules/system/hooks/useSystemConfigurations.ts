import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { fetchMapConfiguration, fetchSystemConfigurations, updateSystemConfiguration } from '../api/systemConfigurationApi';
import type { UpdateSystemConfigurationRequest } from '../model/systemConfiguration';

const systemConfigurationsQueryKey = 'system-configurations';

export const useMapConfiguration = () => useQuery({
  queryKey: ['map-configuration'],
  queryFn: ({ signal }) => fetchMapConfiguration(signal),
  staleTime: 0,
  refetchOnMount: 'always',
});

export const useSystemConfigurations = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [systemConfigurationsQueryKey, request],
  queryFn: () => fetchSystemConfigurations(request),
  enabled,
});

export const useUpdateSystemConfiguration = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateSystemConfigurationRequest }) => (
      updateSystemConfiguration(id, request)
    ),
    onSuccess: async (configuration) => {
      await queryClient.invalidateQueries({ queryKey: [systemConfigurationsQueryKey] });
      if (configuration.configKey === 'panorama.map') {
        await queryClient.invalidateQueries({ queryKey: ['map-configuration'] });
      }
    },
  });
};
