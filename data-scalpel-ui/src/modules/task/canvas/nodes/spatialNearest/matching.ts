import type { SpatialNearestConfiguration, SpatialNearestMatching } from '../../canvasTypes';
import { spatialDistanceUnitOptions } from '../spatialUnits';

export const createNearestMatching = (): SpatialNearestMatching => ({
  semantics: 'EXACT_DISTANCE',
  sourceIdColumnName: '',
  geodesicGeometryMode: 'GEOMETRY',
  connectionLines: {
    enabled: false, outputTableName: '', geometryColumnName: 'connection',
    maximumGeodesicSegmentLength: 10, maximumGeodesicSegmentLengthUnit: 'KILOMETERS',
  },
});

export const usesExactNearest = (configuration: Pick<SpatialNearestConfiguration, 'matching'>) =>
  configuration.matching != null && configuration.matching.semantics !== 'LEGACY_KNN';

export const outputsNearestLines = (configuration: Pick<SpatialNearestConfiguration, 'matching'>) =>
  usesExactNearest(configuration) && configuration.matching?.connectionLines?.enabled === true;

const record = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

export function parseNearestMatching(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialNearestConfiguration, 'matching'> {
  if (!('matching' in raw)) return {};
  if (raw.matching == null) return { matching: null };
  if (!record(raw.matching)) { errors.push(`${path}.matching 必须是对象或 null`); return {}; }
  const value = raw.matching;
  const prefix = `${path}.matching`;
  if (value.semantics != null && value.semantics !== 'EXACT_DISTANCE' && value.semantics !== 'LEGACY_KNN')
    errors.push(`${prefix}.semantics 无效`);
  if (value.sourceIdColumnName != null && typeof value.sourceIdColumnName !== 'string')
    errors.push(`${prefix}.sourceIdColumnName 必须是字符串`);
  if (value.geodesicGeometryMode != null && value.geodesicGeometryMode !== 'POINT_ONLY' && value.geodesicGeometryMode !== 'GEOMETRY')
    errors.push(`${prefix}.geodesicGeometryMode 无效`);
  const matching: SpatialNearestMatching = {
    semantics: value.semantics === 'EXACT_DISTANCE' || value.semantics === 'LEGACY_KNN' ? value.semantics : null,
    sourceIdColumnName: typeof value.sourceIdColumnName === 'string' ? value.sourceIdColumnName : '',
    connectionLines: null,
    geodesicGeometryMode: value.geodesicGeometryMode === 'POINT_ONLY' || value.geodesicGeometryMode === 'GEOMETRY'
      ? value.geodesicGeometryMode : null,
  };
  if (value.connectionLines == null) return { matching };
  if (!record(value.connectionLines)) { errors.push(`${prefix}.connectionLines 必须是对象或 null`); return { matching }; }
  const lines = value.connectionLines;
  if (lines.enabled != null && typeof lines.enabled !== 'boolean') errors.push(`${prefix}.connectionLines.enabled 必须是布尔值或 null`);
  for (const name of ['outputTableName', 'geometryColumnName'])
    if (lines[name] != null && typeof lines[name] !== 'string') errors.push(`${prefix}.connectionLines.${name} 必须是字符串`);
  if (lines.maximumGeodesicSegmentLength != null && (typeof lines.maximumGeodesicSegmentLength !== 'number' || !Number.isFinite(lines.maximumGeodesicSegmentLength)))
    errors.push(`${prefix}.connectionLines.maximumGeodesicSegmentLength 必须是有限数值或 null`);
  const units = spatialDistanceUnitOptions.map(option => option.value);
  const unit = units.find(item => item === lines.maximumGeodesicSegmentLengthUnit) ?? null;
  if (lines.maximumGeodesicSegmentLengthUnit != null && unit == null) errors.push(`${prefix}.connectionLines.maximumGeodesicSegmentLengthUnit 无效`);
  matching.connectionLines = {
    enabled: typeof lines.enabled === 'boolean' ? lines.enabled : null,
    outputTableName: typeof lines.outputTableName === 'string' ? lines.outputTableName : '',
    geometryColumnName: typeof lines.geometryColumnName === 'string' ? lines.geometryColumnName : '',
    maximumGeodesicSegmentLength: typeof lines.maximumGeodesicSegmentLength === 'number' ? lines.maximumGeodesicSegmentLength : null,
    maximumGeodesicSegmentLengthUnit: unit,
  };
  return { matching };
}
