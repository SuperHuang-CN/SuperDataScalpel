import type { SpatialBinAggregateConfiguration } from '../../canvasTypes';

export function parseH3(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialBinAggregateConfiguration, 'h3'> {
  if (!('h3' in raw)) return {};
  if (raw.h3 == null) return { h3: null };
  if (typeof raw.h3 !== 'object' || Array.isArray(raw.h3)) { errors.push(`${path}.h3 必须是对象或 null`); return {}; }
  const value = raw.h3;
  if (!('mode' in value) || value.mode != null && value.mode !== 'RESOLUTION' && value.mode !== 'APPROXIMATE_SIZE') {
    errors.push(`${path}.h3.mode 无效`); return {};
  }
  if (!('resolution' in value) || value.resolution != null && (typeof value.resolution !== 'number' || !Number.isSafeInteger(value.resolution))) {
    errors.push(`${path}.h3.resolution 必须是整数或 null`); return {};
  }
  return { h3: { mode: value.mode ?? null, resolution: value.resolution ?? null } };
}

export function h3SizeSummary(c: SpatialBinAggregateConfiguration): string {
  if (c.h3?.mode === 'RESOLUTION') return c.h3.resolution == null ? '待设置分辨率' : `H3 分辨率 ${c.h3.resolution}`;
  if (c.h3?.mode === 'APPROXIMATE_SIZE') return `近似对边距离 ${c.binSize} ${c.binSizeUnit}`;
  return '待设置 H3 模式';
}
