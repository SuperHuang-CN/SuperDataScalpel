export type PreviewBounds = [number, number, number, number];
export interface SpatialPreviewStatus {
  state: 'NOT_PREPARED' | 'PREPARING' | 'OVERVIEW_READY' | 'READY' | 'UPDATING' | 'FAILED' | 'LIMIT_EXCEEDED' | 'UNSUPPORTED';
  generation: string;
  message: string;
  featureCount: number;
  emptyCount: number;
  bounds: number[];
  imageBounds: number[];
  observedAt: string | null;
  expiresAt: string | null;
}
export const hasSpatialPreviewImage = (status?: SpatialPreviewStatus): status is SpatialPreviewStatus => status?.state === 'OVERVIEW_READY' || status?.state === 'READY';
export interface PreviewImage {
  blob: Blob;
  bounds: PreviewBounds;
  generation: string;
  overview: boolean;
  degraded: boolean;
  fallbackReason?: string;
  resolution: number;
}
const R = 6378137;
export const WORLD = Math.PI * R;
export const toLongitude = (x: number) => x / R * 180 / Math.PI;
export const toLatitude = (y: number) => (2 * Math.atan(Math.exp(y / R)) - Math.PI / 2) * 180 / Math.PI;
export const toX = (longitude: number) => Math.max(-WORLD, Math.min(WORLD, longitude * Math.PI / 180 * R));
export const toY = (latitude: number) => R * Math.log(Math.tan(Math.PI / 4 + Math.max(-85.05112878, Math.min(85.05112878, latitude)) * Math.PI / 360));
export const covers = (outer: PreviewBounds, inner: PreviewBounds) => outer[0] <= inner[0] && outer[1] <= inner[1] && outer[2] >= inner[2] && outer[3] >= inner[3];
export const expandBounds = (bounds: PreviewBounds): PreviewBounds => {
  const dx = (bounds[2] - bounds[0]) * 0.15, dy = (bounds[3] - bounds[1]) * 0.15;
  return [Math.max(-WORLD, bounds[0] - dx), Math.max(-WORLD, bounds[1] - dy), Math.min(WORLD, bounds[2] + dx), Math.min(WORLD, bounds[3] + dy)];
};
/** Only pixels of the verified generation are reused. Geometry is never downloaded to the browser. */
export class PreviewImageCache {
  private images: PreviewImage[] = [];
  private bytes = 0;
  put(image: PreviewImage) {
    if (image.blob.size > 32 * 1024 * 1024 || image.degraded) return;
    this.images.push(image); this.bytes += image.blob.size;
    while (this.bytes > 32 * 1024 * 1024 && this.images.length) this.bytes -= this.images.shift()!.blob.size;
  }
  find(generation: string, bounds: PreviewBounds, resolution: number) {
    return [...this.images].reverse().find((image) => image.generation === generation && covers(image.bounds, bounds) && image.resolution <= resolution * 1.15);
  }
  overview(generation: string) { return [...this.images].reverse().find((image) => image.generation === generation && image.overview); }
  clear() { this.images = []; this.bytes = 0; }
}
