import type { SpatialBinAggregateConfiguration, SpatialPlanarGridOptions } from '../../canvasTypes';

export const newPlanarGrid = (): SpatialPlanarGridOptions => ({ originX: 0, originY: 0, extent: null });
export const planarGridHelp = '坐标使用来源投影 CRS 单位，不随大小单位换算。方格原点是索引 (0,0) 的左下角，六边形原点是中心，方向固定不旋转。业务范围按 [最小值,最大值) 筛选点；保留与范围有正面积相交的完整格网，不裁剪形状。边界点始终保留原分配格网，补空不改变点数。新对齐模式补空最多生成 100 万候选格网，时间窗会进一步增加行数。修改原点、CRS 或大小会改变格网 ID；仅改范围不会改变同一格网 ID。H3 不使用这些平面配置。';

export function parsePlanarGrid(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialBinAggregateConfiguration, 'planarGrid'> {
  if (!('planarGrid' in raw)) return {};
  if (raw.planarGrid == null) return { planarGrid: null };
  const object = (value: unknown, p: string): Record<string, unknown> | null => {
    if (typeof value !== 'object' || value == null || Array.isArray(value)) { errors.push(`${p} 必须是对象`); return null; }
    return value as Record<string, unknown>;
  };
  const number = (value: unknown, p: string): number | null => {
    if (value == null) return null;
    if (typeof value !== 'number' || !Number.isFinite(value)) { errors.push(`${p} 必须是有限数值或 null`); return null; }
    return value;
  };
  const p = `${path}.planarGrid`;
  const grid = object(raw.planarGrid, p);
  if (!grid) return {};
  let extent: SpatialPlanarGridOptions['extent'] = null;
  if (grid.extent != null) {
    const e = object(grid.extent, `${p}.extent`);
    if (e) {
      if (e.mode != null && e.mode !== 'DATA_BOUNDS' && e.mode !== 'EXPLICIT_BOUNDS') errors.push(`${p}.extent.mode 无效`);
      extent = { mode: e.mode === 'DATA_BOUNDS' || e.mode === 'EXPLICIT_BOUNDS' ? e.mode : null,
        minX: number(e.minX, `${p}.extent.minX`), minY: number(e.minY, `${p}.extent.minY`),
        maxX: number(e.maxX, `${p}.extent.maxX`), maxY: number(e.maxY, `${p}.extent.maxY`) };
    }
  }
  return { planarGrid: { originX: number(grid.originX, `${p}.originX`), originY: number(grid.originY, `${p}.originY`), extent } };
}

export function planarGridProblems(grid: SpatialPlanarGridOptions): string[] {
  const issues: string[] = [];
  if (grid.originX == null || grid.originY == null) issues.push('请填写原点 X 和 Y');
  const e = grid.extent;
  if (e && !e.mode) issues.push('请选择范围方式');
  if (e?.mode === 'EXPLICIT_BOUNDS' && (e.minX == null || e.minY == null || e.maxX == null || e.maxY == null
    || e.minX >= e.maxX || e.minY >= e.maxY)) issues.push('范围最小值必须小于最大值，四个坐标均必填');
  return issues;
}
