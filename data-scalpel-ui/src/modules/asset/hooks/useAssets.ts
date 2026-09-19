import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import {
  deleteAsset,
  executeAssetBatchCommand,
  executeAssetCommand,
  fetchAssetCandidates,
  fetchAssets,
  registerAssets,
  updateAsset,
  type AssetCommand,
} from '../api/assetApi';
import type { AssetType, UpdateAssetRequest } from '../model/asset';

const ASSET_QUERY_KEY = ['assets'] as const;

export const useAssets = (request: SearchRequest) => useQuery({
  queryKey: [...ASSET_QUERY_KEY, request],
  queryFn: () => fetchAssets(request),
});

export const useAssetCandidates = (
  assetType: AssetType,
  keyword: string | undefined,
  page: number,
  size: number,
  enabled: boolean,
) => useQuery({
  queryKey: [...ASSET_QUERY_KEY, 'candidates', assetType, keyword ?? '', page, size],
  queryFn: () => fetchAssetCandidates(assetType, keyword, page, size),
  enabled,
});

const useInvalidatingMutation = <TVariables, TResult>(mutationFn: (variables: TVariables) => Promise<TResult>) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ASSET_QUERY_KEY }),
        invalidateDirectoryTree(queryClient, 'ASSET'),
      ]);
    },
  });
};

export const useRegisterAssets = () => useInvalidatingMutation(
  ({ assetType, resourceIds }: { assetType: AssetType; resourceIds: string[] }) => registerAssets(assetType, resourceIds),
);

export const useUpdateAsset = () => useInvalidatingMutation(
  ({ id, request }: { id: string; request: UpdateAssetRequest }) => updateAsset(id, request),
);

export const useAssetCommand = () => useInvalidatingMutation(
  ({ id, command }: { id: string; command: AssetCommand }) => executeAssetCommand(id, command),
);

export const useDeleteAsset = () => useInvalidatingMutation((id: string) => deleteAsset(id));

export const useAssetBatchCommand = () => useInvalidatingMutation(
  (command: 'check-all' | 'sync-all') => executeAssetBatchCommand(command),
);
