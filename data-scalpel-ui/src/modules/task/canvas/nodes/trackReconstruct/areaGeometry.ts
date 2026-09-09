import type { CanvasColumnSchema, SpatialDistanceMethod, TrackAreaGeometryOptions, TrackGeodesicAreaOptions, TrackReconstructOptions } from '../../canvasTypes';
import { spatialDistanceUnitOptions } from '../spatialUnits';
import { bufferWindowProblems, parseBufferWindows } from './bufferWindows';

export const usesAreaGeometry = (options: TrackReconstructOptions | null | undefined) =>
  options != null && options.semantics !== 'LEGACY_POINTS'
  && options.areaGeometry != null && options.areaGeometry.enabled !== false;

export const createAreaGeometryOptions = (polygon: boolean): TrackAreaGeometryOptions => ({
  enabled: true, bufferMode: polygon ? 'NONE' : 'FIELD', bufferField: null, bufferExpression: null, bufferUnit: 'METERS',
});

export const areaBufferLabel = (mode: TrackAreaGeometryOptions['bufferMode'] | undefined) =>
  mode === 'NONE' ? '面观测连接' : mode === 'EXPRESSION' ? '表达式缓冲' : mode === 'FIELD' ? '字段缓冲' : '待配置缓冲';

export function parseAreaGeometry(value: unknown, path: string, errors: string[]): TrackAreaGeometryOptions | null {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) { errors.push(`${path} 必须是对象`); return null; }
  const o = value as Record<string, unknown>;
  if (o.enabled != null && typeof o.enabled !== 'boolean') errors.push(`${path}.enabled 必须是布尔值`);
  const mode = o.bufferMode;
  if (mode != null && mode !== 'NONE' && mode !== 'FIELD' && mode !== 'EXPRESSION') errors.push(`${path}.bufferMode 无效`);
  for (const field of ['bufferField', 'bufferExpression']) {
    if (o[field] != null && typeof o[field] !== 'string') errors.push(`${path}.${field} 必须是字符串`);
  }
  const unit = spatialDistanceUnitOptions.find(option => option.value === o.bufferUnit)?.value;
  if (o.bufferUnit != null && unit == null) errors.push(`${path}.bufferUnit 无效`);
  return {
    ...('enabled' in o ? { enabled: typeof o.enabled === 'boolean' ? o.enabled : null } : {}),
    bufferMode: mode === 'NONE' || mode === 'FIELD' || mode === 'EXPRESSION' ? mode : null,
    bufferField: typeof o.bufferField === 'string' ? o.bufferField : null,
    bufferExpression: typeof o.bufferExpression === 'string' ? o.bufferExpression : null,
    bufferUnit: unit ?? null,
    ...('windowBindings' in o ? {windowBindings:parseBufferWindows(o.windowBindings,`${path}.windowBindings`,errors)} : {}),
    ...('geodesicBoundary' in o ? { geodesicBoundary: parseGeodesicBoundary(o.geodesicBoundary, `${path}.geodesicBoundary`, errors) } : {}),
  };
}

function parseGeodesicBoundary(value: unknown, path: string, errors: string[]): TrackGeodesicAreaOptions | null {
  if (value == null) return null;
  if (typeof value !== 'object' || Array.isArray(value)) { errors.push(`${path} 必须是对象`); return null; }
  const o = value as Record<string, unknown>;
  if (o.maximumSegmentLength != null && (typeof o.maximumSegmentLength !== 'number' || !Number.isFinite(o.maximumSegmentLength)))
    errors.push(`${path}.maximumSegmentLength 必须是有限数值`);
  const unit = spatialDistanceUnitOptions.find(option => option.value === o.maximumSegmentLengthUnit)?.value;
  if (o.maximumSegmentLengthUnit != null && unit == null) errors.push(`${path}.maximumSegmentLengthUnit 无效`);
  return { maximumSegmentLength: typeof o.maximumSegmentLength === 'number' ? o.maximumSegmentLength : null,
    maximumSegmentLengthUnit: unit ?? null };
}

export function areaGeometryProblems(value: TrackAreaGeometryOptions, geometry: CanvasColumnSchema | undefined,
  columns: CanvasColumnSchema[], validationAvailable: boolean, distanceMethod?: SpatialDistanceMethod | null) {
  const problems: string[] = [];
  if (value.bufferMode == null) problems.push('请选择缓冲距离来源');
  if (geometry?.geometry?.dimension && geometry.geometry.dimension !== 'XY') problems.push('面轨迹仅支持 XY Geometry');
  if (value.bufferMode === 'NONE' && geometry?.geometry?.kind === 'POINT') problems.push('点生成面轨迹时必须配置缓冲距离');
  if (value.bufferMode === 'FIELD') {
    const field = columns.find(c => c.name === value.bufferField);
    if (!value.bufferField?.trim()) problems.push('请选择缓冲距离字段');
    else if (validationAvailable && !field) problems.push('缓冲距离字段已失效');
    else if (field && !numericBufferField(field)) problems.push('缓冲距离需要数值字段');
  }
  if (value.bufferMode === 'EXPRESSION' && !value.bufferExpression?.trim()) problems.push('请输入数值表达式');
  if (value.bufferMode === 'EXPRESSION') {
    if ((value.windowBindings?.length ?? 0)>32) problems.push('窗口绑定不能超过 32 项');
    bufferWindowProblems(value.windowBindings ?? [],columns,validationAvailable).forEach((errors,index) => {
      if (errors.length>0) problems.push(`窗口 ${index+1}：${errors.join('；')}`);
    });
  }
  if (value.bufferMode != null && value.bufferMode !== 'NONE' && value.bufferUnit == null) problems.push('请选择距离单位');
  if (distanceMethod === 'GEODESIC') {
    const sampling = value.geodesicBoundary;
    if (sampling?.maximumSegmentLength == null || !Number.isFinite(sampling.maximumSegmentLength) || sampling.maximumSegmentLength <= 0)
      problems.push('面边界采样段长必须为有限正数');
    if (sampling?.maximumSegmentLengthUnit == null || sampling.maximumSegmentLengthUnit === 'SOURCE_CRS_UNIT')
      problems.push('面边界采样须使用线性距离单位');
    if (geometry?.geometry && (geometry.geometry.crs.authority.toUpperCase() !== 'EPSG' || geometry.geometry.crs.code !== 4326))
      problems.push('测地面轨迹需要 EPSG:4326 XY');
    if (value.bufferMode != null && value.bufferMode !== 'NONE' && value.bufferUnit === 'SOURCE_CRS_UNIT')
      problems.push('测地缓冲须使用线性距离单位');
  }
  return problems;
}

export const numericBufferField = (column: CanvasColumnSchema) =>
  ['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL'].includes(column.fieldType);
