import type { SpatialPointClusterConfiguration } from "../../canvasTypes";

export const createSpatialPointClusterConfiguration = (): SpatialPointClusterConfiguration => ({
  dbscan: { mode: 'SPATIAL', timeColumnName: '', searchDuration: null, searchDurationUnit: 'MINUTES' },
  sourceTableName: '',
  pointGeometryColumnName: '',
  featureIdColumnName: '',
  distanceMethod: 'PLANAR',
  parameters: {
    algorithm: 'DBSCAN',
    searchDistance: 200,
    searchDistanceUnit: 'METERS',
    minimumFeatures: 5,
  },
  outputTableName: '',
  clusterIdColumnName: 'cluster_id',
  noiseColumnName: 'is_noise',
});
