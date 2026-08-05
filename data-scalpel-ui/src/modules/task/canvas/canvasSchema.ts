import type { CanvasColumnSchema } from './canvasTypes';

export const platformTypeLabel = (columnSchema: CanvasColumnSchema): string => {
  if (columnSchema.fieldType === 'STRING' && columnSchema.length !== null) {
    return `STRING(${columnSchema.length})`;
  }
  if (columnSchema.fieldType === 'DECIMAL') {
    return `DECIMAL(${columnSchema.precision ?? '?'},${columnSchema.scale ?? '?'})`;
  }
  if (columnSchema.fieldType === 'GEOMETRY') {
    const geometry = columnSchema.geometry;
    return geometry
      ? `${geometry.kind}(${geometry.crs.authority}:${geometry.crs.code},${geometry.dimension})`
      : 'GEOMETRY(?)';
  }
  return columnSchema.fieldType;
};
