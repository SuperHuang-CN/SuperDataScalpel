import type { CanvasColumnSchema } from './canvasTypes';

export const platformTypeLabel = (columnSchema: CanvasColumnSchema): string => {
  if (columnSchema.fieldType === 'STRING' && columnSchema.length !== null) {
    return `STRING(${columnSchema.length})`;
  }
  if (columnSchema.fieldType === 'DECIMAL') {
    return `DECIMAL(${columnSchema.precision ?? '?'},${columnSchema.scale ?? '?'})`;
  }
  return columnSchema.fieldType;
};
