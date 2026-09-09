import type { GeometryUnaryPolicy } from '../canvasTypes';

export function parseUnaryPolicy(raw: Record<string, unknown>, path: string, errors: string[]): { geometryPolicy?: GeometryUnaryPolicy | null } {
  if (!('geometryPolicy' in raw)) return {};
  if (raw.geometryPolicy == null) return { geometryPolicy: null };
  if (raw.geometryPolicy === 'PRESERVE_DIMENSION' || raw.geometryPolicy === 'OUTPUT_XY' || raw.geometryPolicy === 'LEGACY')
    return { geometryPolicy: raw.geometryPolicy };
  errors.push(`${path}.geometryPolicy 无效`);
  return {};
}
