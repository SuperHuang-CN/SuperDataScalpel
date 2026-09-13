import type {
  SpatialJoinDistanceOutput,
  SpatialJoinSpatialNearCondition,
} from '../../canvasTypes';
import { spatialDistanceUnitLabels } from '../spatialUnits';

export const createSpatialJoinSpatialNear = (
  leftGeometryColumnName = '',
  rightGeometryColumnName = '',
): SpatialJoinSpatialNearCondition => ({
  leftGeometryColumnName,
  rightGeometryColumnName,
  distanceMethod: 'PLANAR',
  distance: null,
  distanceUnit: 'SOURCE_CRS_UNIT',
});

export const createSpatialJoinDistanceOutput = (): SpatialJoinDistanceOutput => ({
  enabled: false,
  spatialDistanceColumnName: 'join_distance',
  spatialDistanceUnit: 'METERS',
  temporalDifferenceColumnName: 'join_time_difference',
  temporalDifferenceUnit: 'SECONDS',
});

export const spatialJoinNearSummary = (
  value: SpatialJoinSpatialNearCondition | null | undefined,
): string => {
  if (!value) return '未启用空间距离匹配';
  const method = value.distanceMethod === 'GEODESIC' ? 'Near Geodesic' : 'Near';
  const distance = value.distance == null ? '距离待配置' : `${value.distance}`;
  const unit = value.distanceUnit ? spatialDistanceUnitLabels[value.distanceUnit] : '单位待配置';
  return `${method} · ${value.leftGeometryColumnName || '左 Geometry'} ↔ ${value.rightGeometryColumnName || '右 Geometry'} · ${distance} ${unit}`;
};

export const spatialJoinDistanceOutputSummary = (
  value: SpatialJoinDistanceOutput | null | undefined,
  spatial: boolean,
  temporal: boolean,
): string => {
  if (!value?.enabled) return '不输出匹配距离';
  const outputs = [
    spatial ? (value.spatialDistanceColumnName || '空间距离字段待配置') : null,
    temporal ? (value.temporalDifferenceColumnName || '时间差字段待配置') : null,
  ].filter(Boolean);
  return outputs.length > 0 ? `输出 ${outputs.join('、')}` : '已启用，但尚未配置 Near 条件';
};
