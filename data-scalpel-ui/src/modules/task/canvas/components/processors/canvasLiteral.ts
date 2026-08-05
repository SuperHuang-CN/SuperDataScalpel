import type { CanvasLiteral, PlatformDataType } from '../../canvasTypes';

export const createLiteral = (
  dataType: PlatformDataType,
  value = '',
): CanvasLiteral => ({
  dataType: dataType === 'GEOMETRY' ? 'STRING' : dataType,
  value,
});
