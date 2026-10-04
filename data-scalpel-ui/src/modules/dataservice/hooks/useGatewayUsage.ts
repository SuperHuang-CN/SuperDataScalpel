import { useQuery } from '@tanstack/react-query';
import { fetchGatewayUsage } from '../api/gatewayUsageApi';
export const useGatewayUsage = (range: { from: string; to: string }, enabled: boolean) => useQuery({
 queryKey: ['gateway-access', 'usage', range], queryFn: () => fetchGatewayUsage(range), enabled,
 staleTime: 60_000, refetchInterval: 300_000, refetchIntervalInBackground: false,
});
