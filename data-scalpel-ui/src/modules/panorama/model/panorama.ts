import type { SearchRequest } from '../../../shared/search';
export type ProcessingStatus = 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED';
export type PanoramaAction = 'update' | 'retry-processing' | 'discard-replacement' | 'delete';
export type ValueMode = 'AUTO' | 'MANUAL';
export interface PanoramaMetadata {
  captureTime: string | null; captureOffset: string | null; timeSource: string | null;
  latitude: number | null; longitude: number | null; locationSource: string | null; coordinateDatum: string | null;
  manufacturer: string | null; cameraModel: string | null; exifAltitude: number | null;
  djiAbsoluteAltitude: number | null; djiRelativeAltitude: number | null;
  projection: string | null; panoramaHeading: number | null; aircraftYaw: number | null; warnings: string[];
}
export interface PanoramaContent {
  id: string; originalFilename: string; byteSize: number; width: number; height: number;
  sha256: string; metadata: PanoramaMetadata | null;
}
export interface Panorama {
  id: string; name: string; description: string | null; directoryId: string | null;
  captureTime: string | null; captureOffset: string | null; latitude: number | null; longitude: number | null;
  timeMode: ValueMode; locationMode: ValueMode; contentVersion: number;
  processingStatus: ProcessingStatus; processingError: string | null;
  currentContent: PanoramaContent | null; candidateContent: PanoramaContent | null;
  createdAt: string; updatedAt: string;
}
export interface UpdatePanorama {
  name: string; description?: string | null; directoryId?: string | null; expectedContentVersion: number;
  timeMode: ValueMode; captureTime?: string | null; captureOffset?: string | null;
  locationMode: ValueMode; latitude?: number | null; longitude?: number | null;
}
export interface PanoramaQuery extends SearchRequest { directoryIds?: string[]; uncategorized?: boolean }
export interface MapBounds { west: number; south: number; east: number; north: number }
export interface PanoramaMapPoint { id: string; name: string; latitude: number; longitude: number; captureTime: string | null; contentVersion: number }
export interface PanoramaMapPoints { points: PanoramaMapPoint[]; totalElements: number; truncated: boolean }
export interface PanoramaMapConfig { url: string; attribution: string; maxZoom: number }
export const processingLabels: Record<ProcessingStatus, string> = { QUEUED: '排队中', PROCESSING: '处理中', READY: '已就绪', FAILED: '处理失败' };
export const isProcessing = (p?: Panorama) => p?.processingStatus === 'QUEUED' || p?.processingStatus === 'PROCESSING';
// Capture times without offsets are photo-local wall times; never reinterpret them in the browser's timezone.
export const captureLabel = (p: Pick<Panorama, 'captureTime' | 'captureOffset'>) => p.captureTime ? `${p.captureTime.replace('T', ' ')}${p.captureOffset ? ` ${p.captureOffset}` : '（时区未知）'}` : '—';
export const bytesLabel = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(2)} MiB`;
