import { requestBlobResponse, requestJson, ApiError } from '../../../shared/api/http';
import type { DataModelSpatialPreview } from '../model/dataModel';
import type { PreviewBounds, PreviewImage, SpatialPreviewStatus } from '../model/spatialPreview';

export const previewMetadata = (base: string, signal?: AbortSignal) => requestJson<DataModelSpatialPreview>(`${base}/spatial-preview`, { signal });
export const previewStatus = (base: string, field: string, signal?: AbortSignal) => requestJson<SpatialPreviewStatus>(`${base}/spatial-preview/status?geometryField=${encodeURIComponent(field)}`, { signal, cache: 'no-store' });
export const preparePreview = (base: string, geometryField: string, force: boolean) => requestJson<SpatialPreviewStatus>(`${base}/actions/prepare-spatial-preview`, {
  method: 'POST', body: JSON.stringify({ geometryField, force }),
});
export const previewImage = async (base: string, field: string, generation: string, bounds: PreviewBounds, width: number, height: number, overview: boolean, signal: AbortSignal, viewport?: { clientId: string; sequence: number }): Promise<PreviewImage> => {
  const params = new URLSearchParams({ geometryField: field, generation, bbox: bounds.join(','), width: String(width), height: String(height), overview: String(overview) });
  if (viewport) { params.set("clientId", viewport.clientId); params.set("sequence", String(viewport.sequence)); }
  const result = await requestBlobResponse(`${base}/spatial-preview/map?${params}`, { signal });
  const actual = result.headers.get('X-Spatial-Bounds')?.split(',').map(Number);
  if (result.headers.get('X-Spatial-Generation') !== generation || actual?.length !== 4 || actual.some((n) => !Number.isFinite(n))) throw new ApiError('地图版本已更新，请重新检查', 409);
  return { blob: result.blob, bounds: actual as PreviewBounds, generation, overview: result.headers.get('X-Spatial-Overview') === 'true', degraded: result.headers.get('X-Spatial-Degraded') === 'true', fallbackReason: result.headers.get('X-Spatial-Fallback') ?? undefined, resolution: (actual[2] - actual[0]) / (result.headers.get('X-Spatial-Overview') === 'true' ? 1200 : width) };
};
