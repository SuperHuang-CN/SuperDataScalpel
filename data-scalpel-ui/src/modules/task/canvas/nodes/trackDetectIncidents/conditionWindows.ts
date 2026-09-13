import type { CanvasColumnSchema, TrackIncidentScalar, TrackIncidentWindow } from '../../canvasTypes';

export const incidentWindowKinds: { value: NonNullable<TrackIncidentWindow['kind']>; label: string }[] = [
  { value: 'MEAN', label: '均值' }, { value: 'SUM', label: '合计' }, { value: 'COUNT', label: '非空值数' },
  { value: 'MIN', label: '最小值' }, { value: 'MAX', label: '最大值' },
  { value: 'FIRST', label: '窗口首值' }, { value: 'LAST', label: '窗口末值' },
  { value: 'STDDEV_POP', label: '总体标准差' }, { value: 'VARIANCE_POP', label: '总体方差' },
];

export function incidentWindowErrors(
  windows: TrackIncidentWindow[], columns: CanvasColumnSchema[], schemaAvailable = true,
  pointGeometryColumnName: string | null = null,
  scalars: TrackIncidentScalar[] = [],
) {
  const sourceNames = new Set(columns.map(column => column.name));
  const names = columns.map(column => column.name.toLowerCase());
  const pointGeometry = columns.find(column => column.name === pointGeometryColumnName);
  return windows.map((item, index) => {
    const errors: Partial<Record<keyof TrackIncidentWindow, string>> = {};
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(item.bindingName)) errors.bindingName = '指标名需为字母或下划线开头的 1～128 位标识符';
    else if (names.includes(item.bindingName.toLowerCase()) || windows.some((other, otherIndex) =>
      otherIndex !== index && other.bindingName.toLowerCase() === item.bindingName.toLowerCase())
      || scalars.some(other => other.bindingName.toLowerCase() === item.bindingName.toLowerCase())) {
      errors.bindingName = '指标名称重复或占用了来源字段';
    }
    if ((item.source ?? 'FIELD') !== 'FIELD') {
      const label = item.source === 'TRACK_ACCELERATION' ? '轨迹加速度'
        : item.source === 'TRACK_SPEED' ? '轨迹速度' : '轨迹距离';
      if (!pointGeometryColumnName) errors.source = `${label}需要先选择 Point Geometry`;
      else if (schemaAvailable && (!pointGeometry || pointGeometry.fieldType !== 'GEOMETRY'
        || pointGeometry.geometry?.kind !== 'POINT'
        || pointGeometry.geometry.dimension !== 'XY'
        || pointGeometry.geometry.crs.authority.toUpperCase() !== 'EPSG'
        || pointGeometry.geometry.crs.code !== 4326)) {
        errors.source = `${label}只支持 EPSG:4326 XY 的 Point Geometry`;
      }
    } else if (!item.sourceColumnName || schemaAvailable && !sourceNames.has(item.sourceColumnName)) {
      errors.sourceColumnName = '请选择有效的原始字段，不能引用其他指标';
    }
    if (!item.kind) errors.kind = '请选择函数';
    if (item.startOffset == null || !Number.isInteger(item.startOffset) || item.startOffset <= -2147483648 || item.startOffset > 2147483647)
      errors.startOffset = '请输入有效的有界整数偏移';
    if (item.endOffset == null || !Number.isInteger(item.endOffset) || item.endOffset < -2147483648 || item.endOffset > 2147483647)
      errors.endOffset = '请输入有效的有界整数偏移';
    if (item.startOffset != null && item.endOffset != null && item.startOffset >= item.endOffset)
      errors.endOffset = '终点必须大于起点，终点观测不包含在内';
    return errors;
  });
}

export const incidentScalarSources: { value: NonNullable<TrackIncidentScalar['source']>; label: string }[] = [
  { value: 'TRACK_START_TIME', label: '轨迹开始时间 · Epoch 毫秒' },
  { value: 'TRACK_DURATION', label: '当前轨迹时长 · 毫秒' },
  { value: 'TRACK_CURRENT_TIME', label: '当前观测时间 · Epoch 毫秒' },
  { value: 'TRACK_INDEX', label: '当前观测序号 · 从 0 开始' },
  { value: 'TRACK_POINT_X_AT', label: '相对观测 Point X 坐标' },
  { value: 'TRACK_POINT_Y_AT', label: '相对观测 Point Y 坐标' },
];

export const isIncidentPointCoordinateScalar = (item: TrackIncidentScalar) => (
  item.source === 'TRACK_POINT_X_AT' || item.source === 'TRACK_POINT_Y_AT'
);

export function incidentScalarErrors(
  scalars: TrackIncidentScalar[], columns: CanvasColumnSchema[], windows: TrackIncidentWindow[] = [],
  schemaAvailable = true, pointGeometryColumnName: string | null = null,
) {
  const sourceNames = new Set(columns.map(column => column.name.toLowerCase()));
  const pointGeometry = columns.find(column => column.name === pointGeometryColumnName);
  return scalars.map((item, index) => {
    const errors: Partial<Record<keyof TrackIncidentScalar, string>> = {};
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(item.bindingName)) {
      errors.bindingName = '标量名需为字母或下划线开头的 1～128 位标识符';
    } else if (sourceNames.has(item.bindingName.toLowerCase())
      || windows.some(window => window.bindingName.toLowerCase() === item.bindingName.toLowerCase())
      || scalars.some((other, otherIndex) => otherIndex !== index
        && other.bindingName.toLowerCase() === item.bindingName.toLowerCase())) {
      errors.bindingName = '标量名称重复或占用了来源字段/窗口指标';
    }
    if (!item.source) errors.source = '请选择轨迹标量来源';
    if (isIncidentPointCoordinateScalar(item)) {
      if (item.offset == null || !Number.isInteger(item.offset)
        || item.offset < -2147483648 || item.offset > 2147483647) {
        errors.offset = '请输入 -2147483648～2147483647 的整数观测偏移';
      }
      if (!pointGeometryColumnName) errors.source = 'Point 坐标标量需要先选择 Geometry';
      else if (schemaAvailable && (!pointGeometry || pointGeometry.fieldType !== 'GEOMETRY'
        || pointGeometry.geometry?.kind !== 'POINT')) {
        errors.source = 'Point 坐标标量需要带完整元数据的 Point Geometry';
      }
    }
    return errors;
  });
}

/** Local predicate-editor candidates only, not downstream Schema propagation. Native aggregate
 * result types are resolved by the Compiler; users can explicitly choose the literal type. */
export function incidentConditionColumns(
  columns: CanvasColumnSchema[], windows: TrackIncidentWindow[], scalars: TrackIncidentScalar[] = [],
): CanvasColumnSchema[] {
  const result = [...columns];
  const names = new Set(columns.map(column => column.name.toLowerCase()));
  for (const item of windows) {
    if (!item.bindingName || names.has(item.bindingName.toLowerCase())) continue;
    const source = columns.find(column => column.name === item.sourceColumnName);
    if (!item.kind) continue;
    if ((item.source ?? 'FIELD') !== 'FIELD') {
      const label = item.source === 'TRACK_ACCELERATION' ? '轨迹加速度窗口（米/秒²）'
        : item.source === 'TRACK_SPEED' ? '轨迹速度窗口（米/秒）' : '轨迹累计距离窗口（米）';
      result.push({ name: item.bindingName, fieldType: item.kind === 'COUNT' ? 'LONG' : 'DOUBLE', length: null,
        precision: null, scale: null, geometry: null, nullable: true, defaultValue: null,
        autoIncrement: false, generated: false, comment: `仅用于事件条件的${label}` });
      names.add(item.bindingName.toLowerCase());
      continue;
    }
    if (!source) continue;
    const numeric = ['SUM', 'MEAN', 'STDDEV_POP', 'VARIANCE_POP'].includes(item.kind);
    result.push({ ...source, name: item.bindingName, fieldType: item.kind === 'COUNT' ? 'LONG' : numeric ? 'DOUBLE' : source.fieldType,
      geometry: numeric || item.kind === 'COUNT' ? null : source.geometry,
      nullable: true, autoIncrement: false, generated: false, comment: '仅用于事件条件的窗口指标' });
    names.add(item.bindingName.toLowerCase());
  }
  for (const item of scalars) {
    if (!item.bindingName || !item.source || names.has(item.bindingName.toLowerCase())) continue;
    const coordinate = isIncidentPointCoordinateScalar(item);
    result.push({ name: item.bindingName, fieldType: coordinate ? 'DOUBLE' : 'LONG', length: null,
      precision: null, scale: null, geometry: null, nullable: coordinate, defaultValue: null,
      autoIncrement: false, generated: false,
      comment: coordinate ? '仅用于事件条件的相对观测 Point 坐标' : '仅用于事件条件的轨迹标量' });
    names.add(item.bindingName.toLowerCase());
  }
  return result;
}
