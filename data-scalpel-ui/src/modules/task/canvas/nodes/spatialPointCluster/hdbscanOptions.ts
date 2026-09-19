import type { SpatialHdbscanOptions, SpatialPointClusterConfiguration } from '../../canvasTypes';

export const hdbscanFields = [
  { key: 'probabilityColumnName', label: '成员概率', help: '0～1 的簇成员强度，不是分类准确率；噪声为 0。' },
  { key: 'outlierColumnName', label: '离群程度', help: 'GLOSH 离群分数，越大越离群；不等于 1−成员概率。不足最少要素时为 0。' },
  { key: 'exemplarColumnName', label: '代表点', help: '布尔标记，一个簇可以有多个代表点；不生成中心点，噪声为 false。' },
  { key: 'stabilityColumnName', label: '簇稳定性', help: '同簇相同的归一化持久性，噪声为空。当前采用平台明确的归一化及重复点极限规则，尚未验证与 ArcGIS 数值完全一致。' },
] as const;

export const createHdbscanOptions = (): SpatialHdbscanOptions => ({
  probabilityColumnName: 'cluster_probability', outlierColumnName: 'cluster_outlier',
  exemplarColumnName: 'cluster_exemplar', stabilityColumnName: 'cluster_stability',
});

export const hdbscanHelp = '最少要素包含自身，同时控制密度估计与最小簇大小。HDBSCAN 不使用空间半径或 Linear 时间邻域；保留有效原行并追加四类诊断，跳过 NULL/Empty 点。采用精确半径候选恢复，候选过密时仍可能达到 O(n²)，深层树需要更多 Shuffle/Checkpoint；未完成大规模及 ArcGIS 数值对照，不代表无限容量。';

export function hdbscanFieldErrors(value: SpatialHdbscanOptions, reserved: string[]): Partial<Record<keyof SpatialHdbscanOptions, string>> {
  const errors: Partial<Record<keyof SpatialHdbscanOptions, string>> = {};
  for (const { key } of hdbscanFields) {
    const name = value[key];
    if (!name.trim()) errors[key] = '请输入输出字段名';
    else if (reserved.some(item => item.toLowerCase() === name.toLowerCase())
      || hdbscanFields.some(item => item.key !== key && value[item.key].toLowerCase() === name.toLowerCase())) errors[key] = '输出字段名重复';
  }
  return errors;
}

export function parseHdbscanOptions(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialPointClusterConfiguration, 'hdbscan'> {
  if (!('hdbscan' in raw)) return {};
  if (raw.hdbscan == null) return { hdbscan: null };
  if (typeof raw.hdbscan !== 'object' || Array.isArray(raw.hdbscan)) {
    errors.push(`${path}.hdbscan 必须是对象或 null`); return {};
  }
  const rawValue = raw.hdbscan as Record<string, unknown>;
  const value: SpatialHdbscanOptions = { probabilityColumnName: '', outlierColumnName: '', exemplarColumnName: '', stabilityColumnName: '' };
  for (const { key } of hdbscanFields) {
    const field = rawValue[key];
    if (field != null && typeof field !== 'string') errors.push(`${path}.hdbscan.${key} 必须是字符串`);
    if (typeof field === 'string') value[key] = field;
  }
  return { hdbscan: value };
}
