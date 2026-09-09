import type { SpatialOverlayConfiguration, SpatialOverlayOperation } from '../../canvasTypes';

export const overlayOperationLabels: Record<SpatialOverlayOperation, string> = {
  INTERSECTION: '相交', ERASE: '擦除', UNION: '联合', IDENTITY: '标识', SYMMETRICAL_DIFFERENCE: '对称差',
};
export const overlayOperations = Object.keys(overlayOperationLabels) as SpatialOverlayOperation[];

export const usesOverlayFamily = (configuration: Pick<SpatialOverlayConfiguration, 'geometryPolicy' | 'operation'>) => (
  configuration.geometryPolicy === 'FAMILY_2D'
  || configuration.operation === 'IDENTITY' || configuration.operation === 'SYMMETRICAL_DIFFERENCE'
);

export const overlayFamily = (kind?: string): number => {
  if (kind === 'POINT' || kind === 'MULTIPOINT') return 1;
  if (kind === 'LINESTRING' || kind === 'MULTILINESTRING') return 2;
  if (kind === 'POLYGON' || kind === 'MULTIPOLYGON') return 3;
  return 0;
};

export const overlayCombinationSupported = (operation: SpatialOverlayOperation, left: number, right: number) => {
  if (!left || !right) return false;
  switch (operation) {
    case 'INTERSECTION': return true;
    case 'ERASE': case 'SYMMETRICAL_DIFFERENCE': return left === right;
    case 'UNION': return left === 3 && right === 3;
    case 'IDENTITY': return left === right || right === 3;
  }
};

export const overlayResultLabel = (operation: SpatialOverlayOperation | null, left: number, right: number) => {
  if (!operation || !overlayCombinationSupported(operation, left, right)) return '等待有效图层家族';
  const result = operation === 'INTERSECTION' ? Math.min(left, right) : left;
  return `${['', 'MultiPoint', 'MultiLineString', 'MultiPolygon'][result]} · XY`;
};
