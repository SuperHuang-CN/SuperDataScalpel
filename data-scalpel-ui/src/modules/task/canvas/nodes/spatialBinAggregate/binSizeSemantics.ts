import type { SpatialBinAggregateConfiguration, SpatialBinSizeSemantics } from '../../canvasTypes';

export function parseBinSizeSemantics(value: unknown, path: string, errors: string[]): SpatialBinSizeSemantics | null | undefined {
  if (value == null) return value;
  if (value === 'LEGACY_SIDE_LENGTH' || value === 'HEXAGON_FLAT_TO_FLAT') return value;
  errors.push(`${path} 不是受支持的格网尺寸语义`);
  return undefined;
}

export function binSizeLabel(configuration: Pick<SpatialBinAggregateConfiguration, 'binShape' | 'binSizeSemantics'>): string {
  if (configuration.binShape !== 'HEXAGON') return '边长';
  return configuration.binSizeSemantics === 'HEXAGON_FLAT_TO_FLAT' ? '对边距离' : '边长（旧版）';
}
