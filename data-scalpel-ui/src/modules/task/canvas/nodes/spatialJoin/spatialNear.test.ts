import { describe, expect, it } from 'vitest';
import {
  createSpatialJoinDistanceOutput,
  createSpatialJoinSpatialNear,
  spatialJoinDistanceOutputSummary,
  spatialJoinNearSummary,
} from './spatialNear';

describe('Spatial Join Near drafts', () => {
  it('creates explicit safe defaults', () => {
    expect(createSpatialJoinSpatialNear('shape', 'boundary')).toEqual({
      leftGeometryColumnName: 'shape',
      rightGeometryColumnName: 'boundary',
      distanceMethod: 'PLANAR',
      distance: null,
      distanceUnit: 'SOURCE_CRS_UNIT',
    });
    expect(createSpatialJoinDistanceOutput()).toEqual(expect.objectContaining({
      enabled: false,
      spatialDistanceColumnName: 'join_distance',
      temporalDifferenceColumnName: 'join_time_difference',
    }));
  });

  it('summarizes active matching and output without exposing row values', () => {
    expect(spatialJoinNearSummary({
      leftGeometryColumnName: 'shape',
      rightGeometryColumnName: 'boundary',
      distanceMethod: 'GEODESIC',
      distance: 5,
      distanceUnit: 'KILOMETERS',
    })).toBe('Near Geodesic · shape ↔ boundary · 5 千米');
    expect(spatialJoinDistanceOutputSummary({
      ...createSpatialJoinDistanceOutput(),
      enabled: true,
    }, true, true)).toBe('输出 join_distance、join_time_difference');
  });
});
