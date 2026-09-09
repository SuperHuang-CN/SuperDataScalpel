import type { SpatialDbscanOptions, SpatialPointClusterConfiguration } from '../../canvasTypes';
import { spatialDurationUnitOptions } from '../spatialAggregationOptions';

export const createDbscanOptions = (): SpatialDbscanOptions => ({ mode: 'SPATIAL', timeColumnName: '', searchDuration: null, searchDurationUnit: 'MINUTES' });
export const dbscanHelp = '空间邻域包含自身，核心点须满足最少要素数。Linear 同时要求空间距离与时间差不超过阈值，不按时间桶分开聚类；天为固定 24 小时。只连接核心点形成簇，边界点不能把两个簇合并，跨簇边界点取本次较小簇号。新模式跳过 NULL/Empty 点及 Linear 的 NULL 时间；实际 ID 必须非空且唯一。噪声为 NULL 簇号 + true 标记（对应 ArcGIS -1）。运行复用现有 Checkpoint/GraphFrames，不在编译预检执行聚类。';
export const dbscanModeLabel = (value: SpatialPointClusterConfiguration) => value.parameters.algorithm !== 'DBSCAN'
  ? value.parameters.algorithm : value.dbscan?.mode === 'LINEAR' ? 'Linear 时空' : value.dbscan?.mode === 'SPATIAL' ? '空间密度连通' : value.dbscan && !value.dbscan.mode ? '待选模式' : '旧空间';

export function parseDbscanOptions(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialPointClusterConfiguration, 'dbscan'> {
  if (!('dbscan' in raw)) return {};
  if (raw.dbscan == null) return { dbscan: null };
  if (typeof raw.dbscan !== 'object' || Array.isArray(raw.dbscan)) { errors.push(`${path}.dbscan 必须是对象或 null`); return {}; }
  const v = raw.dbscan as Record<string, unknown>;
  const mode = v.mode === 'LEGACY_SPATIAL' || v.mode === 'SPATIAL' || v.mode === 'LINEAR' ? v.mode : null;
  if (v.mode != null && !mode) errors.push(`${path}.dbscan.mode 无效`);
  if (v.timeColumnName != null && typeof v.timeColumnName !== 'string') errors.push(`${path}.dbscan.timeColumnName 必须是字符串`);
  if (v.searchDuration != null && (typeof v.searchDuration !== 'number' || !Number.isSafeInteger(v.searchDuration))) errors.push(`${path}.dbscan.searchDuration 必须是安全整数或 null`);
  const unit = spatialDurationUnitOptions.find(item => item.value === v.searchDurationUnit)?.value ?? null;
  if (v.searchDurationUnit != null && !unit) errors.push(`${path}.dbscan.searchDurationUnit 无效`);
  return { dbscan: { mode, timeColumnName: typeof v.timeColumnName === 'string' ? v.timeColumnName : '',
    searchDuration: typeof v.searchDuration === 'number' ? v.searchDuration : null, searchDurationUnit: unit } };
}
