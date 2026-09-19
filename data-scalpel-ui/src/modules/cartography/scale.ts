import type { ScaleRange, SpatialStyleDocument } from './model';

export const unlimitedScale = (): ScaleRange => ({ minScaleDenominator: null, maxScaleDenominator: null });
export const scaleRangeError = (range: ScaleRange): string | null => {
  const { minScaleDenominator: min, maxScaleDenominator: max } = range;
  if ([min, max].some(value => value != null && (!Number.isFinite(value) || value <= 0))) return '比例尺分母必须为有限正数';
  return min != null && max != null && min >= max ? '比例尺最小分母必须小于最大分母' : null;
};
export const intersectScale = (left: ScaleRange, right: ScaleRange): ScaleRange => ({
  minScaleDenominator: left.minScaleDenominator == null ? right.minScaleDenominator
    : right.minScaleDenominator == null ? left.minScaleDenominator : Math.max(left.minScaleDenominator, right.minScaleDenominator),
  maxScaleDenominator: left.maxScaleDenominator == null ? right.maxScaleDenominator
    : right.maxScaleDenominator == null ? left.maxScaleDenominator : Math.min(left.maxScaleDenominator, right.maxScaleDenominator),
});
export const documentScaleError = (document: SpatialStyleDocument): string | null => (
  scaleRangeError(document.scaleRange) ?? scaleRangeError(document.labeling.scaleRange)
  ?? (document.labeling.enabled && scaleRangeError(intersectScale(document.scaleRange, document.labeling.scaleRange))
    ? '整体与标注比例尺范围必须有交集' : null)
);
export const isScaleVisible = (range: ScaleRange, scale: number): boolean => (
  (range.minScaleDenominator == null || scale >= range.minScaleDenominator)
  && (range.maxScaleDenominator == null || scale < range.maxScaleDenominator)
);

export interface WmsImageLimits { minimumWidth: number; maximumWidth: number; minimumHeight: number; maximumHeight: number; }
export interface WmsViewport { bbox: [number, number, number, number]; width: number; height: number; scaleDenominator: number; }

/** Square OGC pixels (0.28mm). Match the WMS request, not devicePixelRatio or MapLibre zoom. */
export const buildWmsViewport = (
  bounds: [number, number, number, number], containerWidth: number, containerHeight: number, limits: WmsImageLimits,
): WmsViewport => {
  if (!bounds.every(Number.isFinite) || bounds[2] <= bounds[0] || bounds[3] <= bounds[1]
    || !Number.isFinite(containerWidth) || !Number.isFinite(containerHeight) || containerWidth <= 0 || containerHeight <= 0) {
    throw new Error('地图视口尚未就绪');
  }
  const minimum = Math.max(limits.minimumWidth / containerWidth, limits.minimumHeight / containerHeight);
  const maximum = Math.min(limits.maximumWidth / containerWidth, limits.maximumHeight / containerHeight);
  if (minimum > maximum) throw new Error('地图窗口过于狭长，请调整窗口后渲染');
  const factor = Math.max(minimum, Math.min(1, maximum));
  const width = Math.min(limits.maximumWidth, Math.max(limits.minimumWidth, Math.round(containerWidth * factor)));
  const height = Math.min(limits.maximumHeight, Math.max(limits.minimumHeight, Math.round(containerHeight * factor)));
  const resolution = Math.max((bounds[2] - bounds[0]) / width, (bounds[3] - bounds[1]) / height);
  const mercatorLimit = 20037508.342789244;
  const halfWidth = resolution * width / 2, halfHeight = resolution * height / 2;
  if (halfWidth > mercatorLimit || halfHeight > mercatorLimit) throw new Error('地图视口超出 EPSG:3857 范围，请放大后渲染');
  // Shift, rather than crop, at the world edge: keep square pixels and the same request scale.
  const cx = Math.max(-mercatorLimit + halfWidth, Math.min(mercatorLimit - halfWidth, (bounds[0] + bounds[2]) / 2));
  const cy = Math.max(-mercatorLimit + halfHeight, Math.min(mercatorLimit - halfHeight, (bounds[1] + bounds[3]) / 2));
  return { width, height, bbox: [cx - halfWidth, cy - halfHeight,
    cx + halfWidth, cy + halfHeight], scaleDenominator: resolution / 0.00028 };
};
