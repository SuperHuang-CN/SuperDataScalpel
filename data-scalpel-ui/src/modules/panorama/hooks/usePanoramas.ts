import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { invalidateDirectoryTree } from '../../directory';
import { fetchPanorama, fetchPanoramas, panoramaCommand } from '../api/panoramaApi';
import { isProcessing, type PanoramaAction, type PanoramaQuery, type UpdatePanorama } from '../model/panorama';
export const usePageVisible = () => {
  const [visible, setVisible] = useState(document.visibilityState === 'visible');
  useEffect(() => { const changed = () => setVisible(document.visibilityState === 'visible'); document.addEventListener('visibilitychange', changed); return () => document.removeEventListener('visibilitychange', changed); }, []);
  return visible;
};
export const usePanoramas = (request: PanoramaQuery, enabled = true) => {
  const visible = usePageVisible();
  return useQuery({ queryKey: ['panoramas', 'list', request], queryFn: ({ signal }) => fetchPanoramas(request, signal), enabled,
    refetchInterval: query => enabled && visible && query.state.data?.content.some(isProcessing) ? 2000 : false, refetchIntervalInBackground: false });
};
export const usePanorama = (id: string) => {
  const visible = usePageVisible();
  return useQuery({ queryKey: ['panoramas', 'detail', id], queryFn: ({ signal }) => fetchPanorama(id, signal), enabled: !!id,
    refetchInterval: query => visible && isProcessing(query.state.data) ? 2000 : false, refetchIntervalInBackground: false });
};
export const usePanoramaCommand = () => {
  const client = useQueryClient();
  return useMutation({ mutationFn: ({ id, action, body }: { id: string; action: PanoramaAction; body?: UpdatePanorama | { candidateId: string } }) => panoramaCommand(id, action, body),
    onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['panoramas'] }), invalidateDirectoryTree(client, 'PANORAMA')]); },
    onError: () => { void client.invalidateQueries({ queryKey: ['panoramas'] }); } });
};
