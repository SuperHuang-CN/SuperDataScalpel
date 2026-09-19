import type { SpatialSummarizeWithinConfiguration, SpatialWithinRegions } from '../../canvasTypes';
import { parsePlanarGrid } from '../spatialBinAggregate/planarGrid';
import { spatialDistanceUnitOptions } from '../spatialUnits';

export const createWithinRegions = (): SpatialWithinRegions => ({ mode: 'AREA_TABLE', binShape: 'SQUARE', binSize: null,
  binSizeUnit: 'METERS', planarGrid: { originX: 0, originY: 0, extent: null },
  binIdColumnName: 'bin_id', binGeometryColumnName: 'bin_geometry' });
export const withinGridHelp = '为点、线、面直接生成真实汇总区域。方格大小是边长，六边形是对边距离；原点分别是左下角和中心，坐标使用来源投影 CRS。范围选择完整格网，不裁剪输出形状；线面以完整原要素作分摊分母。Point 使用唯一格网归属，MultiPoint/线面沿区域相交规则，边界可命中相邻区域。最多展开 100 万候选格网，时间窗及分组还会增加结果行数。无隐式投影，不代表已完成 ArcGIS 边界数值对照。';
export const usesGridRegions = (c: SpatialSummarizeWithinConfiguration) => c.regions?.mode === 'PLANAR_GRID';
export function parseWithinRegions(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialSummarizeWithinConfiguration, 'regions'> {
  if (!('regions' in raw)) return {};
  if (raw.regions == null) return { regions: null };
  const p = `${path}.regions`;
  if (typeof raw.regions !== 'object' || Array.isArray(raw.regions)) { errors.push(`${p} 必须是对象或 null`); return {}; }
  const r = raw.regions as Record<string, unknown>;
  const mode = r.mode === 'AREA_TABLE' || r.mode === 'PLANAR_GRID' ? r.mode : null;
  const shape = r.binShape === 'SQUARE' || r.binShape === 'HEXAGON' ? r.binShape : null;
  if (r.mode != null && !mode) errors.push(`${p}.mode 无效`);
  if (r.binShape != null && !shape) errors.push(`${p}.binShape 无效`);
  if (r.binSize != null && (typeof r.binSize !== 'number' || !Number.isFinite(r.binSize))) errors.push(`${p}.binSize 必须是有限数值或 null`);
  const unit = spatialDistanceUnitOptions.find(item => item.value === r.binSizeUnit)?.value ?? null;
  if (r.binSizeUnit != null && !unit) errors.push(`${p}.binSizeUnit 无效`);
  const string = (key: string) => {
    if (r[key] != null && typeof r[key] !== 'string') errors.push(`${p}.${key} 必须是字符串`);
    return typeof r[key] === 'string' ? r[key] : '';
  };
  return { regions: { mode, binShape: shape, binSize: typeof r.binSize === 'number' ? r.binSize : null, binSizeUnit: unit,
    planarGrid: parsePlanarGrid(r, p, errors).planarGrid ?? null, binIdColumnName: string('binIdColumnName'), binGeometryColumnName: string('binGeometryColumnName') } };
}
