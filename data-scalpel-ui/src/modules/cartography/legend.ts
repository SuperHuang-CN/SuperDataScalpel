import type { SpatialStyleDocument, SpatialSymbol } from './model';

export interface SpatialLegendItem { id: string; label: string; symbol: SpatialSymbol; }

export const createSpatialLegend = (document: SpatialStyleDocument | null): SpatialLegendItem[] => {
  if (!document) return [];
  const renderer = document.renderer;
  if (renderer.type === 'SINGLE_SYMBOL') return [{ id: 'single', label: '全部要素', symbol: renderer.symbol }];
  if (renderer.type === 'UNIQUE_VALUE') return [...renderer.uniqueValueRules, ...(renderer.nullRule ? [renderer.nullRule] : []), ...(renderer.elseRule ? [renderer.elseRule] : [])];
  return [...renderer.classBreakRules, ...(renderer.nullRule ? [renderer.nullRule] : [])];
};
