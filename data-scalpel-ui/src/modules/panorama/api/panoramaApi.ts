import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams } from '../../../shared/search';
import type { MapBounds, Panorama, PanoramaAction, PanoramaMapConfig, PanoramaMapPoints, PanoramaQuery, UpdatePanorama } from '../model/panorama';
const path = '/v1/panoramas';
const params = (query: PanoramaQuery) => {
  const value = toSearchParams(query);
  if (query.directoryIds?.length) value.set('directoryIds', query.directoryIds.join(','));
  if (query.uncategorized) value.set('uncategorized', 'true');
  return value;
};
export const fetchPanoramas = (query: PanoramaQuery, signal?: AbortSignal) => requestJson<PageResponse<Panorama>>(`${path}?${params(query)}`, { signal });
export const fetchPanorama = (id: string, signal?: AbortSignal) => requestJson<Panorama>(`${path}/${id}`, { signal });
export const panoramaCommand = (id: string, action: PanoramaAction, body?: UpdatePanorama | { candidateId: string }) => requestJson<Panorama | undefined>(`${path}/${id}/actions/${action}`, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
export const uploadPanorama = (file: File, clientRequestId: string, directoryId?: string, target?: Panorama, signal?: AbortSignal) => {
  const body = new FormData(); body.set('file', file); body.set('clientRequestId', clientRequestId);
  if (directoryId) body.set('directoryId', directoryId);
  if (target) body.set('expectedContentVersion', String(target.contentVersion));
  return requestJson<Panorama>(target ? `${path}/${target.id}/actions/replace` : path, { method: 'POST', body, signal }, 600_000);
};
export const fetchPanoramaImage = (id: string, version: number, kind: 'thumbnail' | 'preview' | 'content', signal?: AbortSignal) => requestBlob(`${path}/${id}/${kind}?version=${version}`, { signal }, kind === 'content' ? 600_000 : 120_000);
export const fetchPanoramaMapConfig = (signal?: AbortSignal) => requestJson<PanoramaMapConfig>(`${path}/map-config`, { signal });
export const fetchPanoramaMapPoints = (query: PanoramaQuery, bounds: MapBounds, signal?: AbortSignal) => {
  const value = params(query); Object.entries(bounds).forEach(([key, number]) => value.set(key, String(number)));
  return requestJson<PanoramaMapPoints>(`${path}/map-points?${value}`, { signal });
};
