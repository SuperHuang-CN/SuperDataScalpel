import type { TrackPathGeometryOptions, TrackReconstructConfiguration, TrackReconstructOptions, TrackSplitBoundaryOption } from '../../canvasTypes';
import { spatialDistanceUnitOptions } from '../spatialUnits';
import { parseAreaGeometry, usesAreaGeometry } from './areaGeometry';

export const boundaryLabels: Record<TrackSplitBoundaryOption, string> = {
  GAP: '留空', FINISH_LAST: '归前段', START_NEXT: '归后段',
};
export const createPathGeometryOptions = (): TrackPathGeometryOptions => ({
  mode: 'METHOD_PATH', maximumGeodesicSegmentLength: 10, maximumGeodesicSegmentLengthUnit: 'KILOMETERS',
});
export const createReconstructionOptions = (): TrackReconstructOptions => ({
  semantics: 'ORDERED_SEGMENTS', orderByColumns: [], splitBoundaryOption: 'GAP', splitExpression: null,
  pathGeometry: createPathGeometryOptions(),
});
export const usesOrderedReconstruction = (options: TrackReconstructOptions | null | undefined) =>
  options != null && options.semantics !== 'LEGACY_POINTS';
export const usesMethodPath = (options: TrackReconstructOptions | null | undefined) =>
  usesOrderedReconstruction(options) && !usesAreaGeometry(options) && options?.pathGeometry != null && options.pathGeometry.mode !== 'LEGACY_VERTEX_LINE';
export const usesSplitExpression = (options: TrackReconstructOptions | null | undefined) =>
  usesOrderedReconstruction(options) && options?.splitExpression != null && options.splitExpression.enabled !== false;

export function parseReconstruction(raw: Record<string, unknown>, path: string, errors: string[]): Pick<TrackReconstructConfiguration, 'reconstruction'> {
  if (!('reconstruction' in raw)) return {};
  if (raw.reconstruction == null) return { reconstruction: null };
  const object = (value: unknown, p: string): Record<string, unknown> => {
    if (typeof value !== 'object' || value == null || Array.isArray(value)) { errors.push(`${p} 必须是对象`); return {}; }
    return value as Record<string, unknown>;
  };
  const string = (value: unknown, p: string) => {
    if (typeof value !== 'string') errors.push(`${p} 必须是字符串`);
    return typeof value === 'string' ? value : '';
  };
  const p = `${path}.reconstruction`;
  const o = object(raw.reconstruction, p);
  const result = createReconstructionOptions();
  // Reading 4.27 options must not silently enable the new path geometry strategy.
  delete result.pathGeometry;
  if ('areaGeometry' in o) result.areaGeometry = parseAreaGeometry(o.areaGeometry, `${p}.areaGeometry`, errors);
  if ('pathGeometry' in o) {
    if (o.pathGeometry == null) result.pathGeometry = null;
    else {
      const g = object(o.pathGeometry, `${p}.pathGeometry`);
      const mode = g.mode;
      if (mode != null && mode !== 'METHOD_PATH' && mode !== 'LEGACY_VERTEX_LINE') errors.push(`${p}.pathGeometry.mode 无效`);
      const length = g.maximumGeodesicSegmentLength;
      if (length != null && (typeof length !== 'number' || !Number.isFinite(length))) errors.push(`${p}.pathGeometry.maximumGeodesicSegmentLength 必须是有限数值`);
      const units = spatialDistanceUnitOptions.map(option => option.value);
      const unit = units.find(value => value === g.maximumGeodesicSegmentLengthUnit);
      if (g.maximumGeodesicSegmentLengthUnit != null && !unit) errors.push(`${p}.pathGeometry.maximumGeodesicSegmentLengthUnit 无效`);
      result.pathGeometry = { mode: mode === 'METHOD_PATH' || mode === 'LEGACY_VERTEX_LINE' ? mode : null,
        maximumGeodesicSegmentLength: typeof length === 'number' ? length : null, maximumGeodesicSegmentLengthUnit: unit ?? null };
    }
  }
  if (o.semantics == null) result.semantics = null;
  else if (o.semantics === 'ORDERED_SEGMENTS' || o.semantics === 'LEGACY_POINTS') result.semantics = o.semantics;
  else errors.push(`${p}.semantics 无效`);
  if (o.splitBoundaryOption == null) result.splitBoundaryOption = null;
  else if (o.splitBoundaryOption === 'GAP' || o.splitBoundaryOption === 'FINISH_LAST' || o.splitBoundaryOption === 'START_NEXT') result.splitBoundaryOption = o.splitBoundaryOption;
  else errors.push(`${p}.splitBoundaryOption 无效`);
  if (o.orderByColumns != null) {
    if (!Array.isArray(o.orderByColumns)) errors.push(`${p}.orderByColumns 必须是数组`);
    else result.orderByColumns = o.orderByColumns.map((v, i) => string(v, `${p}.orderByColumns[${i}]`));
  }
  if (o.splitExpression != null) {
    const rule = object(o.splitExpression, `${p}.splitExpression`);
    const bindings = rule.bindings ?? [];
    if (!Array.isArray(bindings)) errors.push(`${p}.splitExpression.bindings 必须是数组`);
    if (rule.enabled != null && typeof rule.enabled !== 'boolean') errors.push(`${p}.splitExpression.enabled 必须是布尔值`);
    result.splitExpression = {
      expression: string(rule.expression, `${p}.splitExpression.expression`),
      ...('enabled' in rule ? { enabled: typeof rule.enabled === 'boolean' ? rule.enabled : null } : {}),
      bindings: Array.isArray(bindings) ? bindings.map((rawBinding, i) => {
        const b = object(rawBinding, `${p}.splitExpression.bindings[${i}]`);
        if (b.offset != null && (typeof b.offset !== 'number' || !Number.isInteger(b.offset))) errors.push(`${p}.splitExpression.bindings[${i}].offset 必须是整数`);
        return { name: string(b.name, `${p}.splitExpression.bindings[${i}].name`),
          sourceColumnName: string(b.sourceColumnName, `${p}.splitExpression.bindings[${i}].sourceColumnName`),
          offset: typeof b.offset === 'number' && Number.isInteger(b.offset) ? b.offset : null };
      }) : [],
    };
  }
  return { reconstruction: result };
}
